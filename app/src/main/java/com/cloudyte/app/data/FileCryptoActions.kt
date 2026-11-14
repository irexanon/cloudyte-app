package com.cloudyte.app.data

import android.content.Context
import android.net.Uri
import com.cloudyte.app.api.CloudFile
import com.cloudyte.app.api.OfflineFileManager
import com.cloudyte.app.core.cache.StorageManager
import com.cloudyte.app.core.transfer.TransferCommand
import com.cloudyte.app.core.transfer.TransferCoordinator
import com.cloudyte.app.db.TransferDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

object FileCryptoActions {

    private data class FolderEncryptPlan(
        val sourceFolderId: String,
        val sourceFolderName: String,
        val directPlaintextFiles: List<CloudFile>,
        val childFolders: List<FolderEncryptPlan>
    ) {
        val hasAnyPlaintextFiles: Boolean
            get() = directPlaintextFiles.isNotEmpty() || childFolders.any { it.hasAnyPlaintextFiles }
    }

    private fun resolveAccountId(targetFile: CloudFile, accountId: String?): String {
        return targetFile.effectiveAccountId.ifBlank {
            accountId?.takeIf { it.isNotBlank() && it != "all" && it != "root" }.orEmpty()
        }
    }

    suspend fun decryptAndExportFile(
        context: Context,
        accountId: String?,
        targetFile: CloudFile,
        transferRepository: TransferRepository,
        transferCoordinator: TransferCoordinator
    ): String {
        val resolvedAccId = resolveAccountId(targetFile, accountId)
        if (resolvedAccId.isBlank()) {
            return "Select a drive first"
        }
        return if (targetFile.isEncrypted) {
            val decryptedResult = withContext(Dispatchers.IO) {
                OfflineFileManager.prepareDecryptedFileForViewer(context, resolvedAccId, targetFile)
            }
            decryptedResult.fold(
                onSuccess = { decryptedFile ->
                    val exportDir = StorageManager.getPreferredExportDir(context)
                    exportDir.mkdirs()
                    val safeName = targetFile.effectiveName
                        .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                        .ifBlank { "decrypted_file" }
                    val destFile = File(exportDir, safeName)
                    withContext(Dispatchers.IO) {
                        decryptedFile.inputStream().use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    "Decrypted and exported to ${destFile.parentFile?.name ?: "downloads"}/${destFile.name}"
                },
                onFailure = { err ->
                    err.message ?: "Failed to decrypt file"
                }
            )
        } else {
            val exportDir = StorageManager.getPreferredExportDir(context)
            exportDir.mkdirs()
            val safeName = targetFile.effectiveName
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .ifBlank { "download" }
            val destFile = File(exportDir, safeName)
            val transferId = transferRepository.enqueueTransfer(
                accountId = resolvedAccId,
                fileId = targetFile.effectiveId,
                fileName = targetFile.effectiveName,
                localPath = destFile.absolutePath,
                direction = TransferDirection.DOWNLOAD,
                totalBytes = targetFile.sizeBytes,
                isEncrypted = false
            )
            transferCoordinator.dispatch(TransferCommand.Start(transferId))
            "Download added to transfers"
        }
    }

    suspend fun createEncryptedCopy(
        context: Context,
        accountId: String?,
        targetFile: CloudFile,
        fallbackParentFolderId: String?,
        hasExplicitParentFolderId: Boolean = false,
        cloudFileRepository: CloudFileRepository,
        transferRepository: TransferRepository,
        transferCoordinator: TransferCoordinator
    ): String {
        if (targetFile.isEncrypted) {
            return "File is already encrypted"
        }
        val resolvedAccId = resolveAccountId(targetFile, accountId)
        if (resolvedAccId.isBlank()) {
            return "Select a drive first"
        }
        val resolvedParentFolderId = if (hasExplicitParentFolderId) {
            fallbackParentFolderId?.takeUnless { it.isBlank() || it == "root" }
        } else {
            when (val stored = cloudFileRepository.resolveStoredParentFolderId(targetFile.effectiveId)) {
                is CloudFileRepository.StoredParentResult.Found -> stored.parentFolderId
                CloudFileRepository.StoredParentResult.NotFound ->
                    fallbackParentFolderId?.takeUnless { it.isBlank() || it == "root" }
            }
        }

        return if (targetFile.isFolderItem) {
            val plan = withContext(Dispatchers.IO) {
                buildFolderEncryptPlan(
                    cloudFileRepository = cloudFileRepository,
                    accountId = resolvedAccId,
                    folderId = targetFile.effectiveId,
                    folderName = targetFile.effectiveName
                )
            }
            if (!plan.hasAnyPlaintextFiles) {
                return "No unencrypted files found in folder"
            }

            val newRootFolderName = cloudFileRepository.resolveUniqueChildFolderName(
                baseName = targetFile.effectiveName,
                accountId = resolvedAccId,
                parentFolderId = resolvedParentFolderId
            )
            val createdRootFolder = cloudFileRepository.createRemoteFolderAndResolve(
                name = newRootFolderName,
                accountId = resolvedAccId,
                parentFolderId = resolvedParentFolderId
            ).getOrElse { err ->
                return err.message ?: "Could not create encrypted destination folder"
            }
            if (createdRootFolder.effectiveId == targetFile.effectiveId) {
                return "Could not resolve newly created encrypted folder"
            }

            val queued = withContext(Dispatchers.IO) {
                enqueueFolderPlanIntoDestination(
                    context = context,
                    accountId = resolvedAccId,
                    plan = plan,
                    destinationFolderId = createdRootFolder.effectiveId,
                    cloudFileRepository = cloudFileRepository,
                    transferRepository = transferRepository,
                    transferCoordinator = transferCoordinator
                )
            }
            if (queued == 0) {
                "Could not prepare files for encryption"
            } else {
                val noun = if (queued == 1) "file" else "files"
                "Created '${createdRootFolder.effectiveName}' and queued $queued encrypted $noun"
            }
        } else {
            val queued = withContext(Dispatchers.IO) {
                enqueueEncryptedCopy(
                    context = context,
                    accountId = resolvedAccId,
                    targetFile = targetFile,
                    parentFolderId = resolvedParentFolderId,
                    transferRepository = transferRepository,
                    transferCoordinator = transferCoordinator
                )
            }
            if (queued) "Encrypted copy queued for upload" else "Could not prepare file for encryption"
        }
    }

    private suspend fun buildFolderEncryptPlan(
        cloudFileRepository: CloudFileRepository,
        accountId: String,
        folderId: String,
        folderName: String
    ): FolderEncryptPlan {
        cloudFileRepository.refresh(accountId, folderId, force = true)
        val children = cloudFileRepository.observeFolder(accountId, folderId).first()
        val directPlaintextFiles = children.filter { !it.isFolderItem && !it.isEncrypted }
        val childFolders = children
            .filter { it.isFolderItem }
            .map { subfolder ->
                buildFolderEncryptPlan(
                    cloudFileRepository = cloudFileRepository,
                    accountId = accountId,
                    folderId = subfolder.effectiveId,
                    folderName = subfolder.effectiveName
                )
            }
            .filter { it.hasAnyPlaintextFiles }
        return FolderEncryptPlan(
            sourceFolderId = folderId,
            sourceFolderName = folderName,
            directPlaintextFiles = directPlaintextFiles,
            childFolders = childFolders
        )
    }

    private suspend fun enqueueFolderPlanIntoDestination(
        context: Context,
        accountId: String,
        plan: FolderEncryptPlan,
        destinationFolderId: String,
        cloudFileRepository: CloudFileRepository,
        transferRepository: TransferRepository,
        transferCoordinator: TransferCoordinator
    ): Int {
        var queued = 0
        for (child in plan.directPlaintextFiles) {
            if (enqueueEncryptedCopy(
                    context = context,
                    accountId = accountId,
                    targetFile = child,
                    parentFolderId = destinationFolderId,
                    transferRepository = transferRepository,
                    transferCoordinator = transferCoordinator
                )
            ) {
                queued++
            }
        }
        for (childPlan in plan.childFolders) {
            val childDestFolder = cloudFileRepository.createRemoteFolderAndResolve(
                name = childPlan.sourceFolderName,
                accountId = accountId,
                parentFolderId = destinationFolderId
            ).getOrNull() ?: continue
            queued += enqueueFolderPlanIntoDestination(
                context = context,
                accountId = accountId,
                plan = childPlan,
                destinationFolderId = childDestFolder.effectiveId,
                cloudFileRepository = cloudFileRepository,
                transferRepository = transferRepository,
                transferCoordinator = transferCoordinator
            )
        }
        return queued
    }

    private suspend fun enqueueEncryptedCopy(
        context: Context,
        accountId: String,
        targetFile: CloudFile,
        parentFolderId: String?,
        transferRepository: TransferRepository,
        transferCoordinator: TransferCoordinator
    ): Boolean {
        val plaintextResult = OfflineFileManager.prepareDecryptedFileForViewer(context, accountId, targetFile)
        return plaintextResult.fold(
            onSuccess = { localPlaintextFile ->
                val transferId = transferRepository.enqueueTransfer(
                    accountId = accountId,
                    fileName = targetFile.effectiveName,
                    localPath = Uri.fromFile(localPlaintextFile).toString(),
                    remotePath = parentFolderId,
                    direction = TransferDirection.UPLOAD,
                    totalBytes = localPlaintextFile.length(),
                    isEncrypted = true,
                    originalName = targetFile.effectiveName,
                    originalMimeType = targetFile.mimeType,
                    originalSize = localPlaintextFile.length()
                )
                transferCoordinator.dispatch(TransferCommand.Start(transferId))
                true
            },
            onFailure = { false }
        )
    }
}
