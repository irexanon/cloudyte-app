package com.cloudyte.app.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Streaming

interface CloudyteApi {

    @GET("/api/public/settings")
    suspend fun getPublicSettings(): PublicSettingsResponse

    @POST("/api/register")
    suspend fun register(@Body request: RegisterRequest): Response<ResponseBody>

    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("/api/logout")
    suspend fun logout(): Response<Unit>

    @GET("/api/me")
    suspend fun getProfile(): UserProfile

    @POST("/api/user/settings")
    suspend fun updateSettings(@Body request: UserSettingsRequest): Response<Unit>

    @PUT("/api/accounts/{accountId}/encryption/key-backup")
    suspend fun putEncryptionKeyBackup(
        @Path("accountId") accountId: String,
        @Body backup: EncryptionKeyBackup
    ): Response<Unit>

    @GET("/api/accounts/{accountId}/encryption/key-backup")
    suspend fun getEncryptionKeyBackup(
        @Path("accountId") accountId: String
    ): Response<EncryptionKeyBackup>

    @DELETE("/api/accounts/{accountId}/encryption/key-backup")
    suspend fun deleteEncryptionKeyBackup(
        @Path("accountId") accountId: String
    ): Response<Unit>

    @GET("/api/accounts")
    suspend fun getAccounts(): List<Account>

    @DELETE("/api/accounts/{id}")
    suspend fun deleteAccount(@Path("id") id: String): Response<Unit>

    @PATCH("/api/accounts/{id}")
    suspend fun renameAccount(
        @Path("id") id: String,
        @Body request: AccountRenameRequest
    ): Response<Unit>

    @GET("/api/auth/drive-link")
    suspend fun getGoogleDriveLink(): OAuthLinkResponse

    @GET("/api/auth/onedrive-link")
    suspend fun getOneDriveLink(): OAuthLinkResponse

    @POST("/api/tgdrive/register")
    suspend fun registerTgDrive(@Body request: TgDriveRegisterRequest): Response<Unit>

    @POST("/api/tgdrive/{accountId}/import")
    suspend fun importTgDrive(
        @Path("accountId") accountId: String,
        @Body body: RequestBody
    ): Response<TgDriveImportResponse>

    @Streaming
    @GET("/api/tgdrive/{accountId}/export")
    suspend fun exportTgDrive(
        @Path("accountId") accountId: String
    ): Response<ResponseBody>

    @GET("/api/files")
    suspend fun getFiles(
        @Query("accountId") accountId: String?,
        @Query("folderId") folderId: String?,
        @Query("search") search: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
        @Header("If-None-Match") ifNoneMatch: String? = null
    ): Response<List<CloudFile>>

    @POST("/api/folders")
    suspend fun createFolder(
        @Query("accountId") accountId: String?,
        @Body request: FolderRequest
    ): Response<Unit>

    @Multipart
    @POST("/api/upload/{accountId}")
    suspend fun uploadFile(
        @Path("accountId") accountId: String,
        @Query("folderId") folderIdQuery: String? = null,
        @Part file: MultipartBody.Part,
        @Part("folderId") folderIdPart: RequestBody? = null,
        @Part("parentId") parentIdPart: RequestBody? = null,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
        @Header("X-File-Checksum") checksum: String? = null,
        @Header("X-Upload-Session") uploadSession: String? = null
    ): Response<ResponseBody>

    @POST("/api/upload-sessions/{accountId}")
    suspend fun createUploadSession(
        @Path("accountId") accountId: String,
        @Body request: UploadSessionRequest
    ): Response<UploadSessionResponse>

    @GET("/api/upload-sessions/{sessionId}")
    suspend fun getUploadSession(@Path("sessionId") sessionId: String): Response<UploadSessionResponse>

    @Multipart
    @PUT("/api/upload-sessions/{sessionId}/chunks")
    suspend fun uploadChunk(
        @Path("sessionId") sessionId: String,
        @Header("Content-Range") contentRange: String,
        @Part chunk: MultipartBody.Part
    ): Response<UploadChunkResponse>

    @PUT("/api/upload-sessions/{sessionId}/chunks")
    suspend fun uploadChunkStream(
        @Path("sessionId") sessionId: String,
        @Header("Content-Range") contentRange: String,
        @Header("X-Chunk-Offset") chunkOffset: Long,
        @Body chunk: RequestBody
    ): Response<UploadChunkResponse>

    @POST("/api/upload-sessions/{sessionId}/pause")
    suspend fun pauseUploadSession(@Path("sessionId") sessionId: String): Response<UploadSessionResponse>

    @POST("/api/upload-sessions/{sessionId}/resume")
    suspend fun resumeUploadSession(@Path("sessionId") sessionId: String): Response<UploadSessionResponse>

    @POST("/api/upload-sessions/{sessionId}/complete")
    suspend fun completeUploadSession(@Path("sessionId") sessionId: String): Response<UploadCompleteResponse>

    @DELETE("/api/upload-sessions/{sessionId}")
    suspend fun cancelUploadSession(@Path("sessionId") sessionId: String): Response<Unit>

    @GET("/api/media-link/{accountId}/{fileId}")
    suspend fun getMediaLink(
        @Path("accountId") accountId: String,
        @Path("fileId") fileId: String
    ): Response<MediaLinkResponse>

    @Streaming
    @GET("/api/download/{accountId}/{fileId}")
    suspend fun downloadFile(
        @Path("accountId") accountId: String,
        @Path("fileId") fileId: String,
        @Query("preview") preview: Int? = null,
        @Header("Range") range: String? = null
    ): Response<ResponseBody>

    @PATCH("/api/files/{fileId}")
    suspend fun renameFile(
        @Path("fileId") fileId: String,
        @Query("accountId") accountId: String?,
        @Body request: RenameRequest
    ): Response<Unit>

    @DELETE("/api/files/{fileId}")
    suspend fun deleteFile(
        @Path("fileId") fileId: String,
        @Query("accountId") accountId: String?
    ): Response<Unit>

    @POST("/api/files/move")
    suspend fun moveFile(@Body request: MoveCopyRequest): Response<Unit>

    @POST("/api/files/move")
    suspend fun moveFileAsync(
        @Body request: MoveCopyRequest,
        @Query("async") async: Boolean = true
    ): Response<TransferActionResponse>

    @POST("/api/files/copy")
    suspend fun copyFile(@Body request: MoveCopyRequest): Response<Unit>

    @POST("/api/files/copy")
    suspend fun copyFileAsync(
        @Body request: MoveCopyRequest,
        @Query("async") async: Boolean = true
    ): Response<TransferActionResponse>

    @GET("/api/accounts/{accountId}/manifest")
    suspend fun getAccountManifest(
        @Path("accountId") accountId: String,
        @Query("folderId") folderId: String? = null,
        @Query("maxFiles") maxFiles: Int? = null
    ): Response<DriveManifestResponse>

    @POST("/api/transfers/drive")
    suspend fun startDriveTransfer(
        @Body request: DriveTransferRequest
    ): Response<TransferActionResponse>

    @GET("/api/jobs")
    suspend fun getJobs(
        @Query("status") status: String? = null
    ): Response<List<JobStatusResponse>>

    @GET("/api/jobs/{jobId}")
    suspend fun getJobStatus(
        @Path("jobId") jobId: String
    ): Response<JobStatusResponse>

    @POST("/api/jobs/{jobId}/cancel")
    suspend fun cancelJob(
        @Path("jobId") jobId: String
    ): Response<TransferActionResponse>

    @POST("/api/jobs/{jobId}/retry")
    suspend fun retryJob(
        @Path("jobId") jobId: String
    ): Response<TransferActionResponse>

    @POST("/api/backups/batch")
    suspend fun createBatchBackup(
        @Body request: BatchBackupRequest
    ): Response<TransferActionResponse>

    @POST("/api/backups/{jobId}/verify")
    suspend fun verifyBackupJob(
        @Path("jobId") jobId: String
    ): Response<BackupVerifyResponse>

    @POST("/api/backups/{jobId}/restore")
    suspend fun restoreBackupJob(
        @Path("jobId") jobId: String,
        @Body request: BackupRestoreRequest
    ): Response<TransferActionResponse>

    @POST("/api/share")
    suspend fun shareFile(@Body request: ShareRequest): ShareResponse

    @GET("/api/shares")
    suspend fun getShares(): List<ActiveShareDto>

    @DELETE("/api/share/{shareId}")
    suspend fun revokeShare(@Path("shareId") shareId: String): Response<Unit>

    @GET("/api/storage/summary")
    suspend fun getStorageSummary(
        @Query("accountId") accountId: String? = null
    ): StorageSummary

    @GET("/api/trash")
    suspend fun getTrash(
        @Query("accountId") accountId: String? = null
    ): List<CloudFile>

    @POST("/api/trash/restore")
    suspend fun restoreFromTrash(
        @Body request: TrashRestoreRequest
    ): Response<Unit>

    @DELETE("/api/trash/{fileId}")
    suspend fun deleteFromTrash(
        @Path("fileId") fileId: String,
        @Query("accountId") accountId: String
    ): Response<Unit>

    @DELETE("/api/trash")
    suspend fun emptyTrash(
        @Query("accountId") accountId: String? = null
    ): Response<Unit>

    @GET("/api/sessions")
    suspend fun getSessions(): List<ActiveSessionDto>

    @DELETE("/api/sessions/{sessionId}")
    suspend fun revokeSession(
        @Path("sessionId") sessionId: String
    ): Response<Unit>

    @DELETE("/api/sessions")
    suspend fun revokeOtherSessions(): Response<Unit>
}

