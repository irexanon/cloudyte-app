package com.cloudyte.app.data.providers

import com.cloudyte.app.api.CloudyteApi
import com.cloudyte.app.api.UploadChunkResponse
import com.cloudyte.app.api.UploadSessionRequest
import com.cloudyte.app.api.UploadSessionResponse
import okhttp3.MultipartBody
import retrofit2.Response

/**
 * Provider-facing upload contract. The server may route each implementation to
 * a different provider, but the transfer engine only depends on this boundary.
 */
interface CloudProvider {
    val type: String

    suspend fun createUploadSession(
        accountId: String,
        request: UploadSessionRequest
    ): Response<UploadSessionResponse>

    suspend fun uploadChunk(
        sessionId: String,
        contentRange: String,
        chunk: MultipartBody.Part
    ): Response<UploadChunkResponse>

    suspend fun completeUploadSession(sessionId: String): Response<com.cloudyte.app.api.UploadCompleteResponse>
}

abstract class ApiCloudProvider(
    private val api: CloudyteApi
) : CloudProvider {
    override suspend fun createUploadSession(
        accountId: String,
        request: UploadSessionRequest
    ) = api.createUploadSession(accountId, request)

    override suspend fun uploadChunk(
        sessionId: String,
        contentRange: String,
        chunk: MultipartBody.Part
    ) = api.uploadChunk(sessionId, contentRange, chunk)

    override suspend fun completeUploadSession(sessionId: String) =
        api.completeUploadSession(sessionId)
}

class TelegramProvider(api: CloudyteApi) : ApiCloudProvider(api) {
    override val type: String = "telegram"
}

class GoogleDriveProvider(api: CloudyteApi) : ApiCloudProvider(api) {
    override val type: String = "google_drive"
}

class OneDriveProvider(api: CloudyteApi) : ApiCloudProvider(api) {
    override val type: String = "onedrive"
}

class CloudyteProvider(api: CloudyteApi) : ApiCloudProvider(api) {
    override val type: String = "cloudyte"
}
