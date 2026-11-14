package com.cloudyte.app.core.transfer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.api.CloudyteApi
import com.cloudyte.app.api.OfflineFileManager
import com.cloudyte.app.core.cache.PreviewFetcher
import com.cloudyte.app.data.TransferRepository
import com.cloudyte.app.data.providers.ProviderRegistry
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.LocalAccount
import com.cloudyte.app.db.LocalTransfer
import com.cloudyte.app.db.TransferDirection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import java.io.File
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation

@RunWith(RobolectricTestRunner::class)
class PreviewAndRangeRecoveryTest {

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
    fun parseErrorExtractsNestedGoogleDrive416MessageWithoutRawJson() {
        val nestedErrorJson = """
            {"error":{"code":416,"message":"Request range not satisfiable","errors":[{"message":"Request range not satisfiable","domain":"global","reason":"requestedRangeNotSatisfiable"}]}}
        """.trimIndent()
        val topLevelErrorJson = """
            {"code":416,"message":"Request range not satisfiable","errors":[{"message":"Request range not satisfiable"}]}
        """.trimIndent()

        val nestedParsed = ApiClient.parseError(nestedErrorJson, 416)
        val topLevelParsed = ApiClient.parseError(topLevelErrorJson, 416)
        val blankParsed = ApiClient.parseError(null, 416)

        assertEquals("Request range not satisfiable", nestedParsed)
        assertEquals("Request range not satisfiable", topLevelParsed)
        assertFalse(nestedParsed.contains("{"))
        assertTrue(blankParsed.contains("416"))
    }

    @Test
    fun claimClearsStaleErrorFieldsOnResumeOrRetry() = runBlocking {
        val dao = db.transferDao()
        val queueDao = db.transferQueueDao()
        val id = "stale-error-transfer"
        dao.insert(
            LocalTransfer(
                id = id,
                accountId = "acc-1",
                fileName = "photo.jpg",
                direction = TransferDirection.DOWNLOAD.name,
                operationType = "DOWNLOAD",
                state = TransferState.QUEUED.name,
                status = "PENDING",
                totalBytes = 1024L,
                errorMessage = "Request range not satisfiable",
                lastErrorCode = "HTTP_416",
                lastErrorMessage = "Request range not satisfiable",
                lastHttpStatus = 416
            )
        )

        val now = System.currentTimeMillis()
        val claimedRow = queueDao.acquireNextRunnableLease(
            ownerId = "worker-1",
            now = now,
            leaseUntil = now + 30_000L
        )
        assertEquals(id, claimedRow?.id)

        val row = dao.getById(id)!!
        assertEquals(TransferState.RUNNING.name, row.state)
        assertNull(row.errorMessage)
        assertNull(row.lastErrorCode)
        assertNull(row.lastErrorMessage)
        assertNull(row.lastHttpStatus)
    }

    @Test
    fun downloadRecoversFromHttp416WhenPartFileHasInvalidOffset() = runBlocking {
        db.accountDao().insertAccounts(
            listOf(
                LocalAccount(
                    id = "acc-gd",
                    name = "Drive",
                    driveName = "Google Drive",
                    email = "u@test",
                    accountType = "google_drive",
                    usageBytes = 0L,
                    totalBytes = 10_000L,
                    percentage = 0f,
                    fileCount = 0,
                    isDefault = true,
                    channelId = null
                )
            )
        )

        val destFile = File(tempFolder.root, "recovered_image.jpg")
        val partFile = File(destFile.parentFile, "${destFile.name}.part")
        partFile.writeBytes(ByteArray(9999) { 1 }) // stale/corrupt offset larger than remote payload

        val expectedPayload = "verified-full-image-bytes".toByteArray()
        val requestedRanges = mutableListOf<String?>()

        val fakeApi = createStubDownloadApi { _, _, _, range ->
            requestedRanges += range
            if (range != null) {
                Response.error(
                    416,
                    """{"code":416,"message":"Request range not satisfiable"}"""
                        .toResponseBody("application/json".toMediaType())
                )
            } else {
                Response.success(expectedPayload.toResponseBody("image/jpeg".toMediaType()))
            }
        }

        val registry = ProviderRegistry(fakeApi)
        val transfersRepo = TransferRepository(db.transferDao(), registry)
        val executor = CloudTransferOperationExecutor(
            context = context,
            database = db,
            transfers = transfersRepo,
            providers = registry
        )

        val transferId = "dl-416-recover"
        val transfer = LocalTransfer(
            id = transferId,
            accountId = "acc-gd",
            fileId = "file-1",
            remoteId = "file-1",
            fileName = "recovered_image.jpg",
            localPath = destFile.absolutePath,
            direction = TransferDirection.DOWNLOAD.name,
            operationType = "DOWNLOAD",
            providerType = "google_drive",
            totalBytes = expectedPayload.size.toLong(),
            bytesTransferred = 9999L
        )
        db.transferDao().insert(transfer)

        val drainer = RoomTransferQueueDrainer(
            queueDao = db.transferQueueDao(),
            executor = executor,
            ownerId = "test-owner",
            now = { System.currentTimeMillis() },
            maxRetryCount = { 2 },
            maxTransfersPerDrain = 1
        )
        drainer.drain(DrainTrigger.USER_REQUEST, { false }) {}

        assertEquals(listOf("bytes=9999-", null), requestedRanges)
        assertTrue(destFile.exists())
        assertArrayEquals(expectedPayload, destFile.readBytes())
        assertFalse(partFile.exists())
        val updated = db.transferDao().getById(transferId)!!
        assertEquals(TransferState.COMPLETED.name, updated.state)
        assertNull(updated.errorMessage)
    }

    @Test
    fun downloadFinalizesWhenHttp416MatchesCompleteRemoteLength() = runBlocking {
        db.accountDao().insertAccounts(
            listOf(
                LocalAccount(
                    id = "acc-gd",
                    name = "Drive",
                    driveName = "Google Drive",
                    email = "u@test",
                    accountType = "google_drive",
                    usageBytes = 0L,
                    totalBytes = 10_000L,
                    percentage = 0f,
                    fileCount = 0,
                    isDefault = true,
                    channelId = null
                )
            )
        )

        val expectedPayload = "already-complete-part-bytes".toByteArray()
        val destFile = File(tempFolder.root, "already_done.jpg")
        val partFile = File(destFile.parentFile, "${destFile.name}.part")
        partFile.writeBytes(expectedPayload)

        var callCount = 0
        val fakeApi = createStubDownloadApi { _, _, _, _ ->
            callCount++
            val raw = okhttp3.Response.Builder()
                .request(okhttp3.Request.Builder().url("https://example.test/file").build())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(416)
                .message("Range Not Satisfiable")
                .headers(Headers.headersOf("Content-Range", "bytes */${expectedPayload.size}"))
                .build()
            Response.error("Range Not Satisfiable".toResponseBody("text/plain".toMediaType()), raw)
        }

        val registry = ProviderRegistry(fakeApi)
        val transfersRepo = TransferRepository(db.transferDao(), registry)
        val executor = CloudTransferOperationExecutor(
            context = context,
            database = db,
            transfers = transfersRepo,
            providers = registry
        )

        val transferId = "dl-416-complete"
        val transfer = LocalTransfer(
            id = transferId,
            accountId = "acc-gd",
            fileId = "file-2",
            remoteId = "file-2",
            fileName = "already_done.jpg",
            localPath = destFile.absolutePath,
            direction = TransferDirection.DOWNLOAD.name,
            operationType = "DOWNLOAD",
            providerType = "google_drive",
            totalBytes = expectedPayload.size.toLong(),
            bytesTransferred = expectedPayload.size.toLong()
        )
        db.transferDao().insert(transfer)

        val drainer = RoomTransferQueueDrainer(
            queueDao = db.transferQueueDao(),
            executor = executor,
            ownerId = "test-owner",
            now = { System.currentTimeMillis() },
            maxRetryCount = { 2 },
            maxTransfersPerDrain = 1
        )
        drainer.drain(DrainTrigger.USER_REQUEST, { false }) {}

        assertEquals(1, callCount)
        assertTrue(destFile.exists())
        assertArrayEquals(expectedPayload, destFile.readBytes())
        assertEquals(TransferState.COMPLETED.name, db.transferDao().getById(transferId)!!.state)
    }

    @Test
    fun viewingFilesDoesNotEnqueuePersistentTransferQueueRows() = runBlocking {
        val sampleFile = CloudFile(
            id = "view-only-1",
            name = "Screenshot_2026-11-14.jpg",
            type = "jpg",
            mimeType = "image/jpeg",
            size = 204_800L,
            accountId = "acc-1"
        )

        OfflineFileManager.autoCacheOnView(context, "acc-1", sampleFile)
        PreviewFetcher.resolveCachedOnly(context, "acc-1", sampleFile)

        val queued = db.transferDao().observeAllTransfers().first()
        assertTrue("Opening previews must never populate TransferDao", queued.isEmpty())
    }

    private fun createStubDownloadApi(
        onDownloadFile: (String, String, Int?, String?) -> Response<ResponseBody>
    ): CloudyteApi {
        return Proxy.newProxyInstance(
            CloudyteApi::class.java.classLoader,
            arrayOf(CloudyteApi::class.java)
        ) { _, method, args ->
            when (method.name) {
                "downloadFile" -> onDownloadFile(
                    args[0] as String,
                    args[1] as String,
                    args[2] as Int?,
                    args[3] as String?
                )
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
