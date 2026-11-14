package com.cloudyte.app.core.transfer

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cloudyte.app.api.CloudyteApi
import com.cloudyte.app.api.UploadChunkResponse
import com.cloudyte.app.api.UploadCompleteResponse
import com.cloudyte.app.api.UploadSessionRequest
import com.cloudyte.app.api.UploadSessionResponse
import com.cloudyte.app.data.TransferRepository
import com.cloudyte.app.data.providers.ProviderRegistry
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.LocalAccount
import com.cloudyte.app.db.LocalTransfer
import com.cloudyte.app.db.TransferDirection
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import java.io.File
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation

@RunWith(RobolectricTestRunner::class)
class TransferReliabilityAndDeletionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun retryLimitTransitionsToFailedPermanentAfterConfiguredRetries() = runBlocking {
        val dao = db.transferDao()
        dao.insert(
            LocalTransfer(
                id = "retry-cap-test",
                accountId = "a1",
                fileName = "flaky.bin",
                direction = TransferDirection.BACKUP.name,
                operationType = "BACKUP",
                totalBytes = 100L
            )
        )

        val failingExecutor = object : TransferOperationExecutor {
            override suspend fun execute(
                transfer: LocalTransfer,
                leaseOwner: String,
                onProgress: suspend (Long, Long, Long, Long?) -> Unit
            ): TransferOutcome = TransferOutcome.Retryable("IO_FAILURE", "Simulated transient network error")
        }

        var nowMs = 10_000L
        val drainer = RoomTransferQueueDrainer(
            queueDao = db.transferQueueDao(),
            executor = failingExecutor,
            ownerId = "test-owner",
            now = { nowMs },
            maxRetryCount = { 2 },
            maxTransfersPerDrain = 1
        )

        // Attempt 1 (initial) -> attemptCount=1 -> FAILED_RETRYABLE
        drainer.drain(DrainTrigger.USER_REQUEST, { false }) {}
        val after1 = dao.getById("retry-cap-test")!!
        assertEquals(TransferState.FAILED_RETRYABLE.name, after1.state)
        assertEquals(1, after1.attemptCount)

        // Retry 1 (attemptCount=2 <= 2) -> FAILED_RETRYABLE
        nowMs = (after1.nextAttemptAt ?: nowMs) + 1_000L
        drainer.drain(DrainTrigger.USER_REQUEST, { false }) {}
        val after2 = dao.getById("retry-cap-test")!!
        assertEquals(TransferState.FAILED_RETRYABLE.name, after2.state)
        assertEquals(2, after2.attemptCount)

        // Retry 2 exhausted (attemptCount=3 > 2) -> FAILED_PERMANENT
        nowMs = (after2.nextAttemptAt ?: nowMs) + 1_000L
        drainer.drain(DrainTrigger.USER_REQUEST, { false }) {}
        val after3 = dao.getById("retry-cap-test")!!
        assertEquals(TransferState.FAILED_PERMANENT.name, after3.state)
        assertEquals("RETRY_LIMIT_EXCEEDED", after3.lastErrorCode)
    }

    @Test
    fun processDeathRecoverExpiredLeaseAndNeverDeletesLocalFileOnIncompleteUpload() = runBlocking {
        val sourceFile = File(tempFolder.root, "important_doc.pdf").apply {
            writeBytes("do-not-lose-me".toByteArray())
        }
        val dao = db.transferDao()
        val id = "backup-lease-1"
        dao.insert(
            LocalTransfer(
                id = id,
                accountId = "a1",
                fileName = sourceFile.name,
                localPath = Uri.fromFile(sourceFile).toString(),
                localUri = Uri.fromFile(sourceFile).toString(),
                direction = TransferDirection.BACKUP.name,
                operationType = "BACKUP",
                state = TransferState.RUNNING.name,
                status = "RUNNING",
                leaseOwner = "dead-process-1",
                leaseExpiresAt = 5_000L,
                totalBytes = sourceFile.length(),
                bytesTransferred = 4L,
                attemptCount = 1
            )
        )

        // Recover expired lease after process death (now = 10_000L > 5_000L)
        val recoveredCount = db.transferQueueDao().recoverExpiredLeases(10_000L)
        assertEquals(1, recoveredCount)
        val recoveredRow = dao.getById(id)!!
        assertEquals(TransferState.QUEUED.name, recoveredRow.state)
        assertNull(recoveredRow.leaseOwner)
        // Local file MUST still exist and no upload record marked SUCCESS yet.
        assertTrue(sourceFile.exists())
        assertTrue(db.uploadRecordDao().getAllRecords().isEmpty())
    }

    @Test
    fun partialUploadResumesAndDeletesLocalFileOnlyAfterVerifiedCompletion() = runBlocking {
        val sourceFile = File(tempFolder.root, "photo_backup.jpg").apply {
            writeBytes(ByteArray(3 * 1024 * 1024) { (it % 127).toByte() })
        }
        val fileUri = Uri.fromFile(sourceFile).toString()

        db.accountDao().insertAccounts(
            listOf(
                LocalAccount(
                    id = "a1",
                    name = "Drive",
                    driveName = "Google Drive",
                    email = "user@cloudyte.test",
                    accountType = "google_drive",
                    usageBytes = 0L,
                    totalBytes = 1_000_000_000L,
                    percentage = 0f,
                    fileCount = 0,
                    isDefault = true,
                    channelId = null
                )
            )
        )

        val seenIdempotencyKeys = mutableSetOf<String>()
        val uploadedRanges = mutableListOf<String>()
        var failSecondChunkOnce = true
        val createdSessionId = "sess-resume-99"
        var serverReceivedOffset = 0L
        var expectedChecksum: String? = null

        val fakeApi = createStubCloudyteApi(
            onCreateUploadSession = { _, req ->
                seenIdempotencyKeys += req.idempotencyKey
                expectedChecksum = req.checksum
                Response.success(
                    UploadSessionResponse(
                        sessionId = createdSessionId,
                        status = "ACTIVE",
                        receivedBytes = serverReceivedOffset,
                        totalBytes = req.size,
                        chunkSize = 1024L * 1024L,
                        checksum = expectedChecksum,
                        providerType = "google_drive",
                        providerResumable = true,
                        providerCancellable = false
                    )
                )
            },
            onGetUploadSession = { sessionId ->
                Response.success(
                    UploadSessionResponse(
                        sessionId = sessionId,
                        status = "ACTIVE",
                        receivedBytes = serverReceivedOffset,
                        totalBytes = sourceFile.length(),
                        chunkSize = 1024L * 1024L,
                        checksum = expectedChecksum,
                        providerType = "google_drive",
                        providerResumable = true,
                        providerCancellable = false
                    )
                )
            },
            onUploadChunk = { sessionId, contentRange, _ ->
                uploadedRanges += contentRange
                if (failSecondChunkOnce && uploadedRanges.size == 2) {
                    failSecondChunkOnce = false
                    Response.error(503, "Temporary unavailable".toResponseBody("text/plain".toMediaType()))
                } else {
                    val endInclusive = contentRange.substringAfter("-").substringBefore("/").toLong()
                    serverReceivedOffset = endInclusive + 1L
                    Response.success(
                        UploadChunkResponse(
                            sessionId = sessionId,
                            receivedBytes = serverReceivedOffset,
                            totalBytes = sourceFile.length(),
                            nextOffset = serverReceivedOffset,
                            complete = false
                        )
                    )
                }
            },
            onCompleteUploadSession = { sessionId ->
                Response.success(
                    UploadCompleteResponse(
                        success = true,
                        sessionId = sessionId,
                        checksum = expectedChecksum
                    )
                )
            }
        )

        val registry = ProviderRegistry(fakeApi)
        val transfersRepo = TransferRepository(db.transferDao(), registry)
        val executor = CloudTransferOperationExecutor(
            context = context,
            database = db,
            transfers = transfersRepo,
            providers = registry,
            shouldEncryptNewUploads = { false },
            shouldEncryptOfflineFiles = { false },
            shouldDeleteAfterBackupUpload = { true }
        )

        val transferId = "backup-resumable-test"
        db.transferDao().insert(
            LocalTransfer(
                id = transferId,
                accountId = "a1",
                fileName = sourceFile.name,
                localPath = fileUri,
                localUri = fileUri,
                parentFolderId = "root",
                providerType = "google_drive",
                direction = TransferDirection.BACKUP.name,
                operationType = "BACKUP",
                totalBytes = sourceFile.length(),
                idempotencyKey = "idem-99",
                isEncrypted = false
            )
        )

        var nowMs = System.currentTimeMillis()
        val drainer = RoomTransferQueueDrainer(
            queueDao = db.transferQueueDao(),
            executor = executor,
            ownerId = "owner-1",
            now = { nowMs },
            maxRetryCount = { 3 },
            maxTransfersPerDrain = 1
        )

        // First drain fails mid-upload on chunk 2 -> must NOT delete local file and must preserve session metadata
        drainer.drain(DrainTrigger.USER_REQUEST, { false }) {}
        val afterPartial = db.transferDao().getById(transferId)!!
        assertEquals(TransferState.FAILED_RETRYABLE.name, afterPartial.state)
        assertEquals("sess-resume-99", afterPartial.providerSessionId)
        assertTrue(sourceFile.exists())
        assertTrue(db.uploadRecordDao().getAllRecords().isEmpty())

        // Resume after process restart (new drainer instance) -> advance past nextAttemptAt (keeps wall clock ahead too)
        nowMs = maxOf(System.currentTimeMillis(), (afterPartial.nextAttemptAt ?: nowMs) + 1_000L)
        val drainerAfterRestart = RoomTransferQueueDrainer(
            queueDao = db.transferQueueDao(),
            executor = executor,
            ownerId = "owner-2",
            now = { nowMs },
            maxRetryCount = { 3 },
            maxTransfersPerDrain = 1
        )
        drainerAfterRestart.drain(DrainTrigger.APP_START, { false }) {}

        val completed = db.transferDao().getById(transferId)!!
        assertEquals(TransferState.COMPLETED.name, completed.state)
        assertEquals(setOf("idem-99"), seenIdempotencyKeys)
        assertNotNull(db.uploadRecordDao().findLatestSuccessByUri(fileUri, "a1", "root")?.contentHash)
        // Local file safely deleted ONLY after verified upload completion
        assertFalse(sourceFile.exists())
    }

    @Test
    fun localDeletionFailureAfterVerifiedUploadIsReportedSeparatelyWithoutFailingTransfer() = runBlocking {
        Robolectric.buildContentProvider(ReadOnlyDocProvider::class.java)
            .create("com.cloudyte.test.readonly")
        val uriStr = DocumentsContract.buildDocumentUri("com.cloudyte.test.readonly", "locked-doc-1").toString()
        db.accountDao().insertAccounts(
            listOf(
                LocalAccount(
                    id = "a1",
                    name = "Drive",
                    driveName = "Drive",
                    email = "user@cloudyte.test",
                    accountType = "cloudyte",
                    usageBytes = 0L,
                    totalBytes = 1000L,
                    percentage = 0f,
                    fileCount = 0,
                    isDefault = true,
                    channelId = null
                )
            )
        )
        val transfer = LocalTransfer(
            id = "del-fail-1",
            accountId = "a1",
            fileName = "locked.jpg",
            localPath = uriStr,
            localUri = uriStr,
            parentFolderId = "root",
            direction = TransferDirection.BACKUP.name,
            operationType = "BACKUP",
            totalBytes = 42L,
            checksum = "abcd"
        )
        db.transferDao().insert(transfer)

        val failures = mutableListOf<String>()
        val registry = ProviderRegistry(createStubCloudyteApi())
        val executor = CloudTransferOperationExecutor(
            context = context,
            database = db,
            transfers = TransferRepository(db.transferDao(), registry),
            providers = registry,
            shouldDeleteAfterBackupUpload = { true },
            onLocalDeletionFailed = { _, reason -> failures += reason }
        )

        // Calling onCompleted simulates verified upload success while provider refuses deletion
        executor.onCompleted(transfer)

        assertEquals(1, failures.size)
        assertTrue(failures.first().contains("locked.jpg"))
        assertEquals(1, db.uploadRecordDao().countSuccessfulUploads())
    }

    class ReadOnlyDocProvider : ContentProvider() {
        override fun onCreate(): Boolean = true
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            val cols = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            return MatrixCursor(cols).apply {
                addRow(Array<Any?>(cols.size) { "locked-doc-1" })
            }
        }
        override fun getType(uri: Uri): String = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            throw SecurityException("Provider does not allow deleting locked document")
        }
        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
            throw SecurityException("Provider does not allow $method on locked document")
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    private fun createStubCloudyteApi(
        onCreateUploadSession: (String, UploadSessionRequest) -> Response<UploadSessionResponse> = { _, _ ->
            error("Not stubbed")
        },
        onGetUploadSession: (String) -> Response<UploadSessionResponse> = { _ -> error("Not stubbed") },
        onUploadChunk: (String, String, MultipartBody.Part) -> Response<UploadChunkResponse> = { _, _, _ ->
            error("Not stubbed")
        },
        onCompleteUploadSession: (String) -> Response<UploadCompleteResponse> = { _ -> error("Not stubbed") }
    ): CloudyteApi {
        return Proxy.newProxyInstance(
            CloudyteApi::class.java.classLoader,
            arrayOf(CloudyteApi::class.java)
        ) { _, method, args ->
            when (method.name) {
                "createUploadSession" -> onCreateUploadSession(args[0] as String, args[1] as UploadSessionRequest)
                "getUploadSession" -> onGetUploadSession(args[0] as String)
                "uploadChunk" -> onUploadChunk(args[0] as String, args[1] as String, args[2] as MultipartBody.Part)
                "completeUploadSession" -> onCompleteUploadSession(args[0] as String)
                "cancelUploadSession" -> Response.success(Unit)
                else -> {
                    val last = args?.lastOrNull()
                    if (last is Continuation<*>) {
                        error("Unexpected suspend call in stub CloudyteApi: ${method.name}")
                    }
                    null
                }
            }
        } as CloudyteApi
    }
}
