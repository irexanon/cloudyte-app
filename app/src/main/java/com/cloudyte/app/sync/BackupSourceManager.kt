package com.cloudyte.app.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.cloudyte.app.data.UserPreferences
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.db.BackupPresetType
import com.cloudyte.app.db.BackupSourceEntity
import com.cloudyte.app.db.BackupSourcePermissionStatus
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

data class BackupSourceHealth(
    val status: BackupSourcePermissionStatus,
    val canRead: Boolean,
    val canWrite: Boolean,
    val errorMessage: String? = null
)

object BackupSourceManager {

    val standardPresets: List<BackupPresetType> = listOf(
        BackupPresetType.CAMERA,
        BackupPresetType.PICTURES,
        BackupPresetType.SCREENSHOTS,
        BackupPresetType.MOVIES,
        BackupPresetType.DOCUMENTS,
        BackupPresetType.DOWNLOADS
    )

    fun suggestedInitialUri(preset: BackupPresetType): Uri? {
        val docId = when (preset) {
            BackupPresetType.CAMERA -> "primary:DCIM/Camera"
            BackupPresetType.PICTURES -> "primary:Pictures"
            BackupPresetType.SCREENSHOTS -> "primary:Pictures/Screenshots"
            BackupPresetType.MOVIES -> "primary:Movies"
            BackupPresetType.DOCUMENTS -> "primary:Documents"
            BackupPresetType.DOWNLOADS -> "primary:Download"
            BackupPresetType.CUSTOM -> return null
        }
        return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId)
    }

    fun resolveDirectPresetFile(preset: BackupPresetType): File? {
        val rel = preset.relativePath.trim().trim('/')
        if (rel.isBlank()) return null
        val root = Environment.getExternalStorageDirectory() ?: return null
        return File(root, rel)
    }

    fun findSourceForPreset(
        preset: BackupPresetType,
        sources: List<BackupSourceEntity>
    ): BackupSourceEntity? {
        if (preset == BackupPresetType.CUSTOM) return null
        return sources.firstOrNull { it.presetType == preset.name }
            ?: preset.relativePath.takeIf { it.isNotBlank() }?.let { rel ->
                sources.firstOrNull { source ->
                    source.treeUri.endsWith(":$rel", ignoreCase = true) ||
                        source.treeUri.endsWith("%3A$rel", ignoreCase = true) ||
                        source.treeUri.endsWith("/$rel", ignoreCase = true)
                }
            }
    }

    fun findParentTreeGrantForRelativePath(
        context: Context,
        relativePath: String
    ): Pair<Uri, String>? {
        val cleanRel = relativePath.trim().trim('/')
        if (cleanRel.isBlank()) return null
        val persisted = runCatching { context.contentResolver.persistedUriPermissions }
            .getOrDefault(emptyList())
            .filter { it.isReadPermission }
        for (perm in persisted) {
            val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(perm.uri) }.getOrNull() ?: continue
            val treeRel = treeDocId.substringAfter(':', "").trim('/')
            when {
                treeRel.isEmpty() && treeDocId.endsWith(":") -> {
                    // User granted the root of primary storage ("primary:") -> covers all relative paths!
                    return perm.uri to cleanRel
                }
                cleanRel.equals(treeRel, ignoreCase = true) -> {
                    return perm.uri to ""
                }
                cleanRel.startsWith("$treeRel/", ignoreCase = true) -> {
                    val subPath = cleanRel.substring(treeRel.length + 1).trim('/')
                    return perm.uri to subPath
                }
            }
        }
        return null
    }

    fun buildSubfolderTreeUri(parentTreeUri: Uri, subRelativePath: String): Uri {
        val cleanSub = subRelativePath.trim('/')
        if (cleanSub.isBlank()) return parentTreeUri
        return "$parentTreeUri#subpath=$cleanSub".let { Uri.parse(it) }
    }

    fun resolveTreeDocument(context: Context, uriString: String): DocumentFile? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        return resolveTreeDocument(context, uri)
    }

    fun resolveTreeDocument(context: Context, uri: Uri): DocumentFile? {
        return when (uri.scheme?.lowercase()) {
            "file" -> uri.path?.let { path -> DocumentFile.fromFile(File(path)) }
            "content" -> {
                val subPath = uri.fragment
                    ?.takeIf { it.startsWith("subpath=") }
                    ?.removePrefix("subpath=")
                    ?.trim('/')
                    .orEmpty()
                val baseTreeUri = if (uri.fragment != null) {
                    uri.buildUpon().fragment(null).build()
                } else {
                    uri
                }
                val rootDoc = runCatching { DocumentFile.fromTreeUri(context, baseTreeUri) }.getOrNull()
                    ?: return null
                if (subPath.isBlank()) {
                    rootDoc
                } else {
                    var current: DocumentFile? = rootDoc
                    for (segment in subPath.split('/').filter { it.isNotBlank() }) {
                        val next = current?.findFile(segment)
                            ?: current?.listFiles()?.firstOrNull { it.isDirectory && it.name.equals(segment, ignoreCase = true) }
                        if (next == null || !next.isDirectory) {
                            return null
                        }
                        current = next
                    }
                    current
                }
            }
            else -> {
                val path = uri.path
                if (!path.isNullOrBlank() && File(path).exists()) {
                    DocumentFile.fromFile(File(path))
                } else {
                    runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
                }
            }
        }
    }

    fun inspectSourceHealth(context: Context, source: BackupSourceEntity): BackupSourceHealth {
        val uri = runCatching { Uri.parse(source.treeUri) }.getOrNull()
            ?: return BackupSourceHealth(
                status = BackupSourcePermissionStatus.MISSING,
                canRead = false,
                canWrite = false,
                errorMessage = "Invalid folder URI."
            )

        if (uri.scheme.equals("file", ignoreCase = true) || source.treeUri.startsWith("/")) {
            val file = uri.path?.let { File(it) } ?: File(source.treeUri)
            val isExternalSharedPath = file.absolutePath.startsWith(
                runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrDefault("/storage")
            )
            if (isExternalSharedPath && !PermissionsHelper.isFullStorageAccessGranted(context)) {
                return BackupSourceHealth(
                    status = BackupSourcePermissionStatus.REVOKED,
                    canRead = false,
                    canWrite = false,
                    errorMessage = "Full storage access is turned off. Grant All Files Access to resume backups."
                )
            }
            return when {
                !file.exists() -> BackupSourceHealth(
                    status = BackupSourcePermissionStatus.MISSING,
                    canRead = false,
                    canWrite = false,
                    errorMessage = "Folder no longer exists or was moved."
                )
                !file.isDirectory -> BackupSourceHealth(
                    status = BackupSourcePermissionStatus.UNREADABLE,
                    canRead = false,
                    canWrite = false,
                    errorMessage = "Selected path is not a directory."
                )
                !file.canRead() -> BackupSourceHealth(
                    status = BackupSourcePermissionStatus.UNREADABLE,
                    canRead = false,
                    canWrite = false,
                    errorMessage = "Read access denied for this folder."
                )
                else -> BackupSourceHealth(
                    status = BackupSourcePermissionStatus.GRANTED,
                    canRead = true,
                    canWrite = file.canWrite(),
                    errorMessage = null
                )
            }
        }

        val baseTreeUri = if (uri.fragment != null) uri.buildUpon().fragment(null).build() else uri
        val persisted = runCatching { context.contentResolver.persistedUriPermissions }.getOrDefault(emptyList())
        val perm = persisted.firstOrNull {
            it.uri == uri ||
                it.uri == baseTreeUri ||
                it.uri.toString() == source.treeUri ||
                it.uri.toString() == baseTreeUri.toString()
        }
        if (perm == null || !perm.isReadPermission) {
            return BackupSourceHealth(
                status = BackupSourcePermissionStatus.REVOKED,
                canRead = false,
                canWrite = false,
                errorMessage = "Folder access permission was revoked or not persisted. Re-grant access to resume backups."
            )
        }

        return try {
            val doc = resolveTreeDocument(context, uri)
            when {
                doc == null || !doc.exists() -> BackupSourceHealth(
                    status = BackupSourcePermissionStatus.MISSING,
                    canRead = false,
                    canWrite = false,
                    errorMessage = "Backup folder is missing, unmounted, or deleted."
                )
                !doc.isDirectory || !doc.canRead() -> BackupSourceHealth(
                    status = BackupSourcePermissionStatus.UNREADABLE,
                    canRead = false,
                    canWrite = false,
                    errorMessage = "Backup folder cannot be read. Check storage availability or re-grant access."
                )
                else -> {
                    // Trigger a lightweight child listing check to detect dead/unreadable tree providers early
                    runCatching { doc.listFiles() }.onFailure { err ->
                        if (err is SecurityException) {
                            return BackupSourceHealth(
                                status = BackupSourcePermissionStatus.REVOKED,
                                canRead = false,
                                canWrite = false,
                                errorMessage = "Android revoked read permission for this folder. Re-grant access."
                            )
                        }
                        return BackupSourceHealth(
                            status = BackupSourcePermissionStatus.UNREADABLE,
                            canRead = false,
                            canWrite = false,
                            errorMessage = err.localizedMessage ?: "Folder is unreadable."
                        )
                    }
                    BackupSourceHealth(
                        status = BackupSourcePermissionStatus.GRANTED,
                        canRead = true,
                        canWrite = perm.isWritePermission && doc.canWrite(),
                        errorMessage = null
                    )
                }
            }
        } catch (_: SecurityException) {
            BackupSourceHealth(
                status = BackupSourcePermissionStatus.REVOKED,
                canRead = false,
                canWrite = false,
                errorMessage = "Android revoked read permission for this folder. Re-grant access."
            )
        } catch (e: Exception) {
            BackupSourceHealth(
                status = BackupSourcePermissionStatus.UNREADABLE,
                canRead = false,
                canWrite = false,
                errorMessage = e.localizedMessage ?: "Unable to read folder."
            )
        }
    }

    suspend fun migrateLegacyBackupSourceIfNeeded(
        context: Context,
        db: AppDatabase = AppDatabase.getDatabase(context)
    ) {
        val dao = db.backupSourceDao()
        if (dao.getAll().isNotEmpty()) return

        val prefs = UserPreferences.observe(context).first()
        val legacyUri = prefs.backupUri?.takeIf { it.isNotBlank() }
            ?: SyncPreferences.getBackupUri(context).first()?.takeIf { it.isNotBlank() }
            ?: return

        val legacyAccount = prefs.backupAccountId?.takeIf { it.isNotBlank() }
            ?: SyncPreferences.getBackupAccountId(context).first()?.takeIf { it.isNotBlank() }

        val doc = resolveTreeDocument(context, legacyUri)
        val parsed = runCatching { Uri.parse(legacyUri) }.getOrNull()
        val name = doc?.name
            ?: parsed?.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
            ?: "Selected Folder"

        val tempEntity = BackupSourceEntity(
            treeUri = legacyUri,
            displayName = name.ifBlank { "Selected Folder" },
            presetType = BackupPresetType.CUSTOM.name,
            targetAccountId = legacyAccount,
            enabled = true
        )
        val health = inspectSourceHealth(context, tempEntity)
        dao.upsert(
            tempEntity.copy(
                permissionStatus = health.status.name,
                lastError = health.errorMessage
            )
        )
    }

    suspend fun refreshAllSourceHealth(
        context: Context,
        db: AppDatabase = AppDatabase.getDatabase(context)
    ): List<BackupSourceEntity> {
        migrateLegacyBackupSourceIfNeeded(context, db)
        val dao = db.backupSourceDao()
        val now = System.currentTimeMillis()
        val current = dao.getAll()
        current.forEach { source ->
            val health = inspectSourceHealth(context, source)
            if (source.permissionStatus != health.status.name ||
                (health.errorMessage != null && source.lastError != health.errorMessage) ||
                (health.status == BackupSourcePermissionStatus.GRANTED && source.lastError != null && source.lastFailedCount == 0)
            ) {
                dao.updatePermissionStatus(
                    id = source.id,
                    status = health.status.name,
                    error = if (health.status == BackupSourcePermissionStatus.GRANTED && source.lastFailedCount == 0) null else health.errorMessage,
                    updatedAt = now
                )
            }
        }
        return dao.getAll()
    }

    suspend fun persistAndAddSource(
        context: Context,
        treeUri: Uri,
        presetType: BackupPresetType = BackupPresetType.CUSTOM,
        displayNameOverride: String? = null,
        targetAccountId: String? = null,
        targetFolderId: String? = null,
        db: AppDatabase = AppDatabase.getDatabase(context),
        reschedule: Boolean = true
    ): Result<BackupSourceEntity> {
        return runCatching {
            takePersistablePermission(context, treeUri)
            val dao = db.backupSourceDao()
            val uriString = treeUri.toString()
            val existing = dao.findByTreeUri(uriString)
            val doc = resolveTreeDocument(context, treeUri)
                ?: throw IllegalStateException("Selected folder could not be opened.")
            if (!doc.exists() || !doc.isDirectory || !doc.canRead()) {
                throw IllegalStateException("Selected folder is missing or unreadable.")
            }

            val resolvedName = displayNameOverride?.takeIf { it.isNotBlank() }
                ?: deriveDisplayName(treeUri, doc.name, presetType)
            val now = System.currentTimeMillis()
            val candidate = BackupSourceEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                treeUri = uriString,
                displayName = resolvedName,
                presetType = presetType.name,
                targetAccountId = targetAccountId ?: existing?.targetAccountId,
                targetFolderId = targetFolderId ?: existing?.targetFolderId,
                enabled = true,
                permissionStatus = BackupSourcePermissionStatus.GRANTED.name,
                lastScanAt = existing?.lastScanAt ?: 0L,
                lastBackupAt = existing?.lastBackupAt ?: 0L,
                lastSummary = existing?.lastSummary,
                lastError = null,
                lastQueuedCount = existing?.lastQueuedCount ?: 0,
                lastSkippedCount = existing?.lastSkippedCount ?: 0,
                lastFailedCount = 0,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
            dao.upsert(candidate)
            UserPreferences.setBackupUri(context, uriString)
            SyncLogger.log("Added backup folder '${candidate.displayName}'", SyncLogLevel.INFO)
            if (reschedule) {
                AutoSyncManager.requestReschedule(context)
            }
            candidate
        }
    }

    suspend fun regrantSource(
        context: Context,
        sourceId: String,
        grantedTreeUri: Uri,
        db: AppDatabase = AppDatabase.getDatabase(context),
        reschedule: Boolean = true
    ): Result<BackupSourceEntity> {
        return runCatching {
            takePersistablePermission(context, grantedTreeUri)
            val dao = db.backupSourceDao()
            val existing = dao.findById(sourceId)
                ?: throw IllegalStateException("Backup folder entry no longer exists.")
            val doc = resolveTreeDocument(context, grantedTreeUri)
                ?: throw IllegalStateException("Granted folder could not be opened.")
            if (!doc.exists() || !doc.isDirectory || !doc.canRead()) {
                throw IllegalStateException("Granted folder is still unreadable or missing.")
            }
            val newUriString = grantedTreeUri.toString()
            val collision = dao.findByTreeUri(newUriString)?.takeIf { it.id != sourceId }
            val preset = runCatching { BackupPresetType.valueOf(existing.presetType) }
                .getOrDefault(BackupPresetType.CUSTOM)
            val now = System.currentTimeMillis()
            if (collision != null) {
                dao.deleteById(existing.id)
                val updatedCollision = collision.copy(
                    enabled = true,
                    permissionStatus = BackupSourcePermissionStatus.GRANTED.name,
                    lastError = null,
                    updatedAt = now
                )
                dao.upsert(updatedCollision)
                if (reschedule) AutoSyncManager.requestReschedule(context)
                updatedCollision
            } else {
                val updated = existing.copy(
                    treeUri = newUriString,
                    displayName = deriveDisplayName(grantedTreeUri, doc.name, preset),
                    permissionStatus = BackupSourcePermissionStatus.GRANTED.name,
                    lastError = null,
                    updatedAt = now
                )
                dao.upsert(updated)
                UserPreferences.setBackupUri(context, newUriString)
                SyncLogger.log("Re-granted access for '${updated.displayName}'", SyncLogLevel.SUCCESS)
                if (reschedule) AutoSyncManager.requestReschedule(context)
                updated
            }
        }
    }

    suspend fun setSourceEnabled(
        context: Context,
        sourceId: String,
        enabled: Boolean,
        db: AppDatabase = AppDatabase.getDatabase(context),
        reschedule: Boolean = true
    ) {
        val dao = db.backupSourceDao()
        val existing = dao.findById(sourceId) ?: return
        dao.upsert(existing.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
        SyncLogger.log(
            if (enabled) "Enabled backup folder '${existing.displayName}'"
            else "Paused backup folder '${existing.displayName}'",
            SyncLogLevel.INFO
        )
        if (reschedule) AutoSyncManager.requestReschedule(context)
    }

    suspend fun setSourceTargetAccount(
        context: Context,
        sourceId: String,
        targetAccountId: String?,
        targetDriveLabel: String? = null,
        db: AppDatabase = AppDatabase.getDatabase(context),
        reschedule: Boolean = true
    ) {
        val dao = db.backupSourceDao()
        val existing = dao.findById(sourceId) ?: return
        val normalizedTarget = targetAccountId?.takeIf { it.isNotBlank() }
        val driveChanged = existing.targetAccountId != normalizedTarget
        dao.upsert(
            existing.copy(
                targetAccountId = normalizedTarget,
                targetFolderId = if (driveChanged) null else existing.targetFolderId,
                updatedAt = System.currentTimeMillis()
            )
        )
        val destDesc = if (normalizedTarget == null) {
            "default backup drive"
        } else {
            targetDriveLabel?.takeIf { it.isNotBlank() } ?: normalizedTarget
        }
        SyncLogger.log(
            "Updated target drive for '${existing.displayName}' → $destDesc",
            SyncLogLevel.INFO
        )
        if (reschedule) AutoSyncManager.onFolderOrAccountChanged(context)
    }

    suspend fun removeSource(
        context: Context,
        sourceId: String,
        db: AppDatabase = AppDatabase.getDatabase(context),
        reschedule: Boolean = true
    ) {
        val dao = db.backupSourceDao()
        val existing = dao.findById(sourceId) ?: return
        dao.deleteById(sourceId)

        val remaining = dao.getAll()
        val existingUri = runCatching { Uri.parse(existing.treeUri) }.getOrNull()
        val existingBaseUri = existingUri?.let { if (it.fragment != null) it.buildUpon().fragment(null).build() else it }
        if (existingBaseUri != null && remaining.none {
                val u = runCatching { Uri.parse(it.treeUri) }.getOrNull()
                val base = u?.let { parsed -> if (parsed.fragment != null) parsed.buildUpon().fragment(null).build() else parsed }
                base == existingBaseUri || it.treeUri == existing.treeUri
            }
        ) {
            if (existingBaseUri.scheme.equals("content", ignoreCase = true)) {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        existingBaseUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
            }
        }
        val firstRemaining = remaining.firstOrNull()
        UserPreferences.setBackupUri(context, firstRemaining?.treeUri)
        SyncLogger.log(
            "Removed backup folder '${existing.displayName}'. Remote files remain untouched.",
            SyncLogLevel.INFO
        )
        if (reschedule) AutoSyncManager.requestReschedule(context)
    }

    private fun takePersistablePermission(context: Context, uri: Uri) {
        if (!uri.scheme.equals("content", ignoreCase = true)) return
        val baseUri = if (uri.fragment != null) uri.buildUpon().fragment(null).build() else uri
        val contentResolver = context.contentResolver
        try {
            contentResolver.takePersistableUriPermission(
                baseUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some document providers only allow read grants; fall back to read-only.
            runCatching {
                contentResolver.takePersistableUriPermission(
                    baseUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    private fun deriveDisplayName(uri: Uri, docName: String?, presetType: BackupPresetType): String {
        val raw = docName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        return when {
            presetType != BackupPresetType.CUSTOM && raw != null -> "${presetType.label} ($raw)"
            raw != null -> raw
            else -> presetType.label
        }
    }
}
