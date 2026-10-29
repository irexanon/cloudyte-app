package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.api.Account
import com.example.api.CloudFile
import com.example.api.CloudyteApi
import com.example.api.FolderRequest
import com.example.api.LoginRequest
import com.example.api.LoginResponse
import com.example.api.MoveCopyRequest
import com.example.api.OAuthLinkResponse
import com.example.api.PublicSettingsResponse
import com.example.api.RegisterRequest
import com.example.api.RenameRequest
import com.example.api.ShareRequest
import com.example.api.ShareResponse
import com.example.api.StorageCategories
import com.example.api.StorageSummary
import com.example.api.TgDriveRegisterRequest
import com.example.api.UserProfile
import com.example.api.UserSettingsRequest
import com.example.db.AppDatabase
import com.example.db.LocalCloudFile
import com.example.db.LocalStorageSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.util.concurrent.Executor

/** Builds a genuine non-success HTTP 304 response for Retrofit's response wrapper. */
@Suppress("UNCHECKED_CAST")
private fun <T> notModifiedResponse(headers: Headers = Headers.headersOf()): Response<T> {
    val raw = okhttp3.Response.Builder()
        .request(okhttp3.Request.Builder().url("https://example.test/files").build())
        .protocol(okhttp3.Protocol.HTTP_1_1).code(304).message("Not Modified").headers(headers).build()
    val ctor = Response::class.java.getDeclaredConstructor(okhttp3.Response::class.java, Any::class.java, ResponseBody::class.java)
    ctor.isAccessible = true
    return ctor.newInstance(raw, null, null) as Response<T>
}

class FakeCloudyteApi : CloudyteApi {
    var getFilesHandler: suspend (
        accountId: String?,
        folderId: String?,
        search: String?,
        cursor: String?,
        limit: Int?,
        ifNoneMatch: String?
    ) -> Response<List<CloudFile>> = { _, _, _, _, _, _ ->
        Response.success(emptyList())
    }

    var renameHandler: suspend (
        fileId: String,
        accountId: String?,
        request: RenameRequest
    ) -> Response<Unit> = { _, _, _ ->
        Response.success(Unit)
    }

    var accountsList: List<Account> = emptyList()

    override suspend fun getPublicSettings(): PublicSettingsResponse = PublicSettingsResponse()
    override suspend fun register(request: RegisterRequest): Response<ResponseBody> =
        Response.success("{}".toResponseBody("application/json".toMediaType()))
    override suspend fun login(request: LoginRequest): Response<LoginResponse> =
        Response.success(LoginResponse(token = "test"))
    override suspend fun logout(): Response<Unit> = Response.success(Unit)
    override suspend fun getProfile(): UserProfile = UserProfile(id = "u1", username = "tester")
    override suspend fun updateSettings(request: UserSettingsRequest): Response<Unit> = Response.success(Unit)
    override suspend fun getAccounts(): List<Account> = accountsList
    override suspend fun deleteAccount(id: String): Response<Unit> = Response.success(Unit)
    override suspend fun getGoogleDriveLink(): OAuthLinkResponse = OAuthLinkResponse()
    override suspend fun getOneDriveLink(): OAuthLinkResponse = OAuthLinkResponse()
    override suspend fun registerTgDrive(request: TgDriveRegisterRequest): Response<Unit> = Response.success(Unit)

    override suspend fun getFiles(
        accountId: String?,
        folderId: String?,
        search: String?,
        cursor: String?,
        limit: Int?,
        ifNoneMatch: String?
    ): Response<List<CloudFile>> {
        return getFilesHandler(accountId, folderId, search, cursor, limit, ifNoneMatch)
    }

    override suspend fun createFolder(accountId: String?, request: FolderRequest): Response<Unit> =
        Response.success(Unit)

    override suspend fun uploadFile(
        accountId: String,
        folderIdQuery: String?,
        file: MultipartBody.Part,
        folderIdPart: RequestBody?,
        parentIdPart: RequestBody?
    ): Response<ResponseBody> = Response.success("{}".toResponseBody("application/json".toMediaType()))

    override suspend fun downloadFile(accountId: String, fileId: String, preview: Int?): ResponseBody =
        "".toResponseBody("application/octet-stream".toMediaType())

    override suspend fun renameFile(
        fileId: String,
        accountId: String?,
        request: RenameRequest
    ): Response<Unit> {
        return renameHandler(fileId, accountId, request)
    }

    override suspend fun deleteFile(fileId: String, accountId: String?): Response<Unit> =
        Response.success(Unit)

    override suspend fun moveFile(request: MoveCopyRequest): Response<Unit> = Response.success(Unit)
    override suspend fun copyFile(request: MoveCopyRequest): Response<Unit> = Response.success(Unit)
    override suspend fun shareFile(request: ShareRequest): ShareResponse = ShareResponse()

    var storageSummaryHandler: suspend (accountId: String?) -> StorageSummary = { accountId ->
        StorageSummary(
            accountId = accountId ?: "all",
            accountType = "gdrive",
            usedBytes = 100L,
            totalBytes = 1000L,
            fileCount = 5,
            categories = StorageCategories(images = 60L, documents = 40L)
        )
    }

    var getStorageSummaryHandler: suspend (accountId: String?) -> StorageSummary
        get() = storageSummaryHandler
        set(value) {
            storageSummaryHandler = value
        }

    override suspend fun getStorageSummary(accountId: String?): StorageSummary {
        return storageSummaryHandler(accountId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CloudFileRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var fakeApi: FakeCloudyteApi
    private lateinit var repository: CloudFileRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directExecutor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        fakeApi = FakeCloudyteApi()
        repository = CloudFileRepository(db, fakeApi)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun optimisticRename_succeedsAndKeepsNewNameInRoom() = runTest {
        val original = CloudFile(id = "file_1", name = "OriginalReport.pdf", type = "pdf")
        db.cloudFileDao().insertFiles(listOf(LocalCloudFile.fromCloudFile(original, "acc1", "root")))

        var nameObservedDuringApiCall: String? = null
        fakeApi.renameHandler = { fileId, _, _ ->
            // Verify Room was ALREADY updated optimistically before the network call finishes
            nameObservedDuringApiCall = db.cloudFileDao().getFileById(fileId)?.name
            Response.success(Unit)
        }

        val result = repository.rename("file_1", "acc1", "RenamedReport.pdf")

        assertTrue(result.isSuccess)
        assertEquals("RenamedReport.pdf", nameObservedDuringApiCall)
        assertEquals("RenamedReport.pdf", db.cloudFileDao().getFileById("file_1")?.name)
    }

    @Test
    fun optimisticRename_rollsBackToOriginalNameOnFailure() = runTest {
        val original = CloudFile(id = "file_2", name = "Budget2026.xlsx", type = "file")
        db.cloudFileDao().insertFiles(listOf(LocalCloudFile.fromCloudFile(original, "acc1", "root")))

        var optimisticNameDuringCall: String? = null
        fakeApi.renameHandler = { fileId, _, _ ->
            optimisticNameDuringCall = db.cloudFileDao().getFileById(fileId)?.name
            Response.error(
                500,
                """{"error":"Server rename failure"}""".toResponseBody("application/json".toMediaType())
            )
        }

        val result = repository.rename("file_2", "acc1", "CorruptedName.xlsx")

        assertTrue(result.isFailure)
        // Optimistic update happened first...
        assertEquals("CorruptedName.xlsx", optimisticNameDuringCall)
        // ...and was rolled back to the original name when the API failed
        assertEquals("Budget2026.xlsx", db.cloudFileDao().getFileById("file_2")?.name)
    }

    @Test
    fun refresh_doesNotClearListWhileFetchingPages() = runTest {
        // Seed initial cached files in Room
        val cached1 = CloudFile(id = "f1", name = "Cached1.jpg", type = "image")
        val cached2 = CloudFile(id = "f2", name = "Cached2.jpg", type = "image")
        db.cloudFileDao().insertFiles(
            listOf(
                LocalCloudFile.fromCloudFile(cached1, "acc1", "root"),
                LocalCloudFile.fromCloudFile(cached2, "acc1", "root")
            )
        )

        val emissions = mutableListOf<List<CloudFile>>()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeFolder("acc1", "root").toList(emissions)
        }

        // Simulate 2-page cursor response where f1 is kept, f2 is removed, and f3 + f4 are added
        fakeApi.getFilesHandler = { _, _, _, cursor, _, _ ->
            // Verify Room is still non-empty while fetching pages
            assertTrue(db.cloudFileDao().getFilesForFolder("acc1", "root").isNotEmpty())
            if (cursor == null) {
                val page1 = listOf(
                    CloudFile(id = "f1", name = "Cached1.jpg", type = "image"),
                    CloudFile(id = "f3", name = "NewPage1.jpg", type = "image")
                )
                Response.success(page1, Headers.headersOf("X-Next-Cursor", "page_2"))
            } else {
                val page2 = listOf(
                    CloudFile(id = "f4", name = "NewPage2.jpg", type = "image")
                )
                Response.success(page2)
            }
        }

        val refreshResult = repository.refresh("acc1", "root")
        assertTrue(refreshResult.isSuccess)

        // Verify the list was NEVER cleared to empty during refresh
        assertTrue(emissions.isNotEmpty())
        assertFalse("List must never be cleared during refresh", emissions.any { it.isEmpty() })

        // Verify final state has [f1, f3, f4] and stale f2 was removed
        val finalIds = db.cloudFileDao().getFilesForFolder("acc1", "root").map { it.id }.toSet()
        assertEquals(setOf("f1", "f3", "f4"), finalIds)

        collectJob.cancel()
    }

    @Test
    fun refresh_skipsFreshFolderAndForceUsesPersistedEtagFor304() = runTest {
        var now = 1_000_000L
        repository = CloudFileRepository(db, fakeApi) { now }
        var calls = 0
        val seenEtags = mutableListOf<String?>()
        val file = CloudFile(id = "cached-file", name = "Cached.pdf", type = "file")
        fakeApi.getFilesHandler = { _, _, _, _, _, etag ->
            calls++; seenEtags += etag
            if (etag == "\"v1\"") notModifiedResponse(Headers.headersOf("ETag", "\"v1\""))
            else Response.success(listOf(file), Headers.headersOf("ETag", "\"v1\""))
        }
        assertTrue(repository.refresh("acc1", "root").isSuccess)
        assertEquals(1, calls)
        repository = CloudFileRepository(db, fakeApi) { now }
        assertTrue(repository.refresh("acc1", "root").isSuccess)
        assertEquals(1, calls)
        assertTrue(repository.refresh("acc1", "root", force = true).isSuccess)
        assertEquals(2, calls)
        assertEquals("\"v1\"", seenEtags.last())
        now += CloudFileRepository.FILE_CACHE_TTL_MS + 1
        assertTrue(repository.refresh("acc1", "root").isSuccess)
        assertEquals(3, calls)
    }

    @Test
    fun concurrentFolderRefreshesAreDeduplicated() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var calls = 0
        fakeApi.getFilesHandler = { _, _, _, _, _, _ ->
            calls++; gate.await()
            Response.success(listOf(CloudFile(id = "same", name = "Item.txt", type = "file")),
                Headers.headersOf("ETag", "\"same\""))
        }
        val first = async { repository.refresh("acc1", "root") }
        val second = async { repository.refresh("acc1", "root") }
        testScheduler.runCurrent()
        assertEquals(1, calls)
        gate.complete(Unit); advanceUntilIdle()
        assertTrue(first.await().isSuccess); assertTrue(second.await().isSuccess)
        assertEquals(1, calls)
    }

    @Test
    fun failedLaterPageDoesNotPolluteCompleteCachedFolder() = runTest {
        val old = CloudFile(id = "old-file", name = "AlreadyCached.txt", type = "file")
        db.cloudFileDao().insertFiles(listOf(LocalCloudFile.fromCloudFile(old, "acc1", "root")))
        fakeApi.getFilesHandler = { _, _, _, cursor, _, _ ->
            if (cursor == null) Response.success(
                listOf(CloudFile(id = "partial-file", name = "PartialPage.txt", type = "file")),
                Headers.headersOf("X-Next-Cursor", "page_2"))
            else Response.error(500, """{"error":"page failed"}""".toResponseBody("application/json".toMediaType()))
        }
        assertTrue(repository.refresh("acc1", "root", force = true).isFailure)
        assertEquals(listOf("old-file"), db.cloudFileDao().getFilesForFolder("acc1", "root").map { it.id })
    }

    @Test
    fun observeStorage_emitsSavedValueImmediatelyAndRefreshesAtMostOncePer60Seconds() = runTest {
        repository = CloudFileRepository(db, fakeApi) { 100_000L + testScheduler.currentTime }
        val savedSummary = StorageSummary(
            accountId = "acc1",
            accountType = "gdrive",
            usedBytes = 500L,
            totalBytes = 10_000L,
            fileCount = 12,
            categories = StorageCategories(images = 300L, documents = 200L)
        )
        // Seed old updatedAt so the first background refresh executes
        db.storageSummaryDao().insert(
            LocalStorageSummary.fromStorageSummary(
                summary = savedSummary,
                fallbackAccountId = "acc1",
                updatedAt = 0L
            )
        )

        var apiCalls = 0
        fakeApi.getStorageSummaryHandler = { accountId ->
            apiCalls++
            StorageSummary(
                accountId = accountId ?: "acc1",
                accountType = "gdrive",
                usedBytes = 1500L + apiCalls,
                totalBytes = 10_000L,
                fileCount = 25,
                categories = StorageCategories(images = 1000L, documents = 500L + apiCalls)
            )
        }

        val emissions = mutableListOf<StorageSummary?>()
        val job1 = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeStorage("acc1").toList(emissions)
        }
        advanceUntilIdle()

        // Emits cached value immediately, then refreshed value from background API call
        assertTrue(emissions.size >= 2)
        assertEquals(500L, emissions.first()?.usedBytes)
        assertEquals(1501L, emissions.last()?.usedBytes)
        assertEquals(1, apiCalls)
        job1.cancel()

        // Observing again within 60 seconds should NOT trigger another network call
        val secondEmissions = mutableListOf<StorageSummary?>()
        val job2 = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeStorage("acc1").toList(secondEmissions)
        }
        advanceUntilIdle()
        assertEquals(1, apiCalls)
        assertEquals(1501L, secondEmissions.last()?.usedBytes)
        job2.cancel()

        // Advance past 60 seconds: observing again triggers a new background refresh
        advanceTimeBy(61_000L)
        val thirdEmissions = mutableListOf<StorageSummary?>()
        val job3 = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeStorage("acc1").toList(thirdEmissions)
        }
        advanceUntilIdle()
        assertEquals(2, apiCalls)
        assertEquals(1502L, thirdEmissions.last()?.usedBytes)
        job3.cancel()
    }
}
