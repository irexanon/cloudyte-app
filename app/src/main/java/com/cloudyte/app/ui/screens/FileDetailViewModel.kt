package com.cloudyte.app.ui.screens

import android.app.Application
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cloudyte.app.CloudyteApplication
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.api.FileRepository
import com.cloudyte.app.api.OfflineFileManager
import com.cloudyte.app.api.ShareRequest
import com.cloudyte.app.core.security.EncryptedSharingNotSupportedException
import com.cloudyte.app.data.FileCryptoActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FileDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CloudyteApplication
    private val cloudFileRepository = app.container.cloudFileRepository
    private val transferRepository = app.container.transferRepository
    private val transferCoordinator = app.container.transferCoordinator

    private val _file = MutableStateFlow<CloudFile?>(null)
    val file: StateFlow<CloudFile?> = _file.asStateFlow()

    private val _shareUrl = MutableStateFlow<String?>(null)
    val shareUrl: StateFlow<String?> = _shareUrl.asStateFlow()

    private val _shareError = MutableStateFlow<String?>(null)
    val shareError: StateFlow<String?> = _shareError.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _isProcessingCryptoAction = MutableStateFlow(false)
    val isProcessingCryptoAction: StateFlow<Boolean> = _isProcessingCryptoAction.asStateFlow()

    fun loadFile(fileId: String) {
        _file.value = FileRepository.getFile(fileId)
        viewModelScope.launch {
            val dbFile = cloudFileRepository.getFileById(fileId)
            if (dbFile != null) {
                _file.value = dbFile
            }
        }
    }

    fun shareFile(accountId: String, fileId: String) {
        viewModelScope.launch {
            val current = _file.value ?: cloudFileRepository.getFileById(fileId)
            if (current?.isEncrypted == true) {
                val ex = EncryptedSharingNotSupportedException()
                _shareError.value = ex.message ?: "Encrypted sharing is not supported"
                return@launch
            }
            try {
                val response = ApiClient.apiService.shareFile(ShareRequest(accountId, fileId))
                val resolvedUrl = response.effectiveUrl
                if (resolvedUrl.isNotBlank()) {
                    _shareUrl.value = resolvedUrl
                } else {
                    _shareError.value = response.error?.takeIf { it.isNotBlank() }
                        ?: "Server did not return a public share link."
                }
            } catch (e: retrofit2.HttpException) {
                Log.e("FileDetailViewModel", "Share HTTP error", e)
                val rawErr = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                _shareError.value = ApiClient.parseError(rawErr, e.code())
            } catch (e: Exception) {
                Log.e("FileDetailViewModel", "Share error", e)
                _shareError.value = e.localizedMessage ?: "Failed to create public share link."
            }
        }
    }

    fun clearShareError() {
        _shareError.value = null
    }

    fun downloadOrDecryptExportFile(
        accountId: String,
        targetFile: CloudFile,
        onFeedback: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isProcessingCryptoAction.value = true
            try {
                val feedback = FileCryptoActions.decryptAndExportFile(
                    context = getApplication(),
                    accountId = accountId,
                    targetFile = targetFile,
                    transferRepository = transferRepository,
                    transferCoordinator = transferCoordinator
                )
                onFeedback(feedback)
            } catch (e: Exception) {
                onFeedback(e.message ?: "Download failed")
            } finally {
                _isProcessingCryptoAction.value = false
            }
        }
    }

    fun createEncryptedCopy(
        accountId: String,
        targetFile: CloudFile,
        onFeedback: (String) -> Unit
    ) {
        if (targetFile.isEncrypted) {
            onFeedback("File is already encrypted")
            return
        }
        viewModelScope.launch {
            _isProcessingCryptoAction.value = true
            try {
                val feedback = FileCryptoActions.createEncryptedCopy(
                    context = getApplication(),
                    accountId = accountId,
                    targetFile = targetFile,
                    fallbackParentFolderId = null,
                    cloudFileRepository = cloudFileRepository,
                    transferRepository = transferRepository,
                    transferCoordinator = transferCoordinator
                )
                onFeedback(feedback)
            } catch (e: Exception) {
                onFeedback(e.message ?: "Could not queue encrypted copy")
            } finally {
                _isProcessingCryptoAction.value = false
            }
        }
    }

    fun renameFile(accountId: String, fileId: String, newName: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = cloudFileRepository.rename(fileId, accountId, newName)
            if (result.isSuccess) {
                _file.value = cloudFileRepository.getFileById(fileId)
                onSuccess()
            } else {
                Log.e("FileDetailViewModel", "Rename could not be queued", result.exceptionOrNull())
            }
        }
    }

    fun deleteFile(accountId: String, fileId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isDeleting.value = true
            try {
                val result = cloudFileRepository.delete(fileId, accountId)
                if (result.isSuccess) onSuccess()
                else Log.e("FileDetailViewModel", "Delete could not be queued", result.exceptionOrNull())
            } finally {
                _isDeleting.value = false
            }
        }
    }

    fun clearShareUrl() {
        _shareUrl.value = null
    }
}
