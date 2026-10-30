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

    @GET("/api/accounts")
    suspend fun getAccounts(): List<Account>

    @DELETE("/api/accounts/{id}")
    suspend fun deleteAccount(@Path("id") id: String): Response<Unit>

    @GET("/api/auth/drive-link")
    suspend fun getGoogleDriveLink(): OAuthLinkResponse

    @GET("/api/auth/onedrive-link")
    suspend fun getOneDriveLink(): OAuthLinkResponse

    @POST("/api/tgdrive/register")
    suspend fun registerTgDrive(@Body request: TgDriveRegisterRequest): Response<Unit>

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

    @Multipart
    @PUT("/api/upload-sessions/{sessionId}/chunks")
    suspend fun uploadChunk(
        @Path("sessionId") sessionId: String,
        @Header("Content-Range") contentRange: String,
        @Part chunk: MultipartBody.Part
    ): Response<UploadChunkResponse>

    @POST("/api/upload-sessions/{sessionId}/complete")
    suspend fun completeUploadSession(@Path("sessionId") sessionId: String): Response<UploadCompleteResponse>

    @Streaming
    @GET("/api/download/{accountId}/{fileId}")
    suspend fun downloadFile(
        @Path("accountId") accountId: String,
        @Path("fileId") fileId: String,
        @Query("preview") preview: Int? = null
    ): ResponseBody

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

    @POST("/api/files/copy")
    suspend fun copyFile(@Body request: MoveCopyRequest): Response<Unit>

    @POST("/api/share")
    suspend fun shareFile(@Body request: ShareRequest): ShareResponse

    @GET("/api/storage/summary")
    suspend fun getStorageSummary(
        @Query("accountId") accountId: String? = null
    ): StorageSummary
}
