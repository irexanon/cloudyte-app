package com.cloudyte.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LocalCloudFile::class,
        LocalAccount::class,
        LocalUploadRecord::class,
        LocalExclusion::class,
        LocalTransfer::class,
        LocalStorageSummary::class,
        LocalFolderSyncMeta::class,
        LocalFileMutation::class,
        LocalFileTombstone::class,
        ThumbnailEntity::class,
        BackupSourceEntity::class
    ],
    version = 15,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cloudFileDao(): CloudFileDao
    abstract fun accountDao(): AccountDao
    abstract fun uploadRecordDao(): UploadRecordDao
    abstract fun exclusionDao(): ExclusionDao
    abstract fun transferDao(): TransferDao
    abstract fun storageSummaryDao(): StorageSummaryDao
    abstract fun folderSyncMetaDao(): FolderSyncMetaDao
    abstract fun fileMutationDao(): FileMutationDao
    abstract fun fileTombstoneDao(): FileTombstoneDao
    abstract fun thumbnailDao(): ThumbnailDao
    abstract fun transferQueueDao(): TransferQueueDao
    abstract fun backupSourceDao(): BackupSourceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(): AppDatabase? = INSTANCE

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cloud_files ADD COLUMN updatedAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE cloud_files ADD COLUMN thumbUrl TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cloud_files_accountId_folderId ON cloud_files(accountId, folderId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS exclusions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        pattern TEXT NOT NULL,
                        isRegex INTEGER NOT NULL DEFAULT 0,
                        enabled INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS transfers (
                        id TEXT PRIMARY KEY NOT NULL,
                        accountId TEXT NOT NULL,
                        fileId TEXT,
                        fileName TEXT NOT NULL,
                        localPath TEXT,
                        remotePath TEXT,
                        direction TEXT NOT NULL,
                        status TEXT NOT NULL,
                        bytesTransferred INTEGER NOT NULL DEFAULT 0,
                        totalBytes INTEGER NOT NULL DEFAULT 0,
                        errorMessage TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS storage_summary (
                        accountId TEXT PRIMARY KEY NOT NULL,
                        accountType TEXT NOT NULL,
                        usedBytes INTEGER NOT NULL DEFAULT 0,
                        totalBytes INTEGER,
                        fileCount INTEGER,
                        categoriesJson TEXT,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS folder_sync_meta (
                        accountKey TEXT NOT NULL,
                        folderId TEXT NOT NULL,
                        etag TEXT,
                        lastSyncedAt INTEGER NOT NULL,
                        PRIMARY KEY(accountKey, folderId)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transfers_status_updatedAt ON transfers(status, updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transfers_accountId_createdAt ON transfers(accountId, createdAt)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transfers ADD COLUMN providerSessionId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE transfers ADD COLUMN chunkSize INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transfers ADD COLUMN nextChunkIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transfers ADD COLUMN checksum TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE transfers ADD COLUMN idempotencyKey TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE transfers ADD COLUMN attemptCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transfers_idempotencyKey ON transfers(idempotencyKey)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS file_mutations (
                        operationId TEXT NOT NULL PRIMARY KEY,
                        accountId TEXT,
                        operation TEXT NOT NULL,
                        fileId TEXT,
                        name TEXT,
                        parentFolderId TEXT NOT NULL DEFAULT 'root',
                        optimisticFileId TEXT,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_file_mutations_status_createdAt ON file_mutations(status, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_file_mutations_accountId_parentFolderId ON file_mutations(accountId, parentFolderId)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transfers ADD COLUMN state TEXT NOT NULL DEFAULT 'QUEUED'")
                db.execSQL("ALTER TABLE transfers ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transfers ADD COLUMN operationType TEXT NOT NULL DEFAULT 'UPLOAD'")
                db.execSQL("ALTER TABLE transfers ADD COLUMN providerType TEXT")
                db.execSQL("ALTER TABLE transfers ADD COLUMN remoteId TEXT")
                db.execSQL("ALTER TABLE transfers ADD COLUMN parentFolderId TEXT")
                db.execSQL("ALTER TABLE transfers ADD COLUMN localUri TEXT")
                db.execSQL("ALTER TABLE transfers ADD COLUMN bytesPerSecond INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transfers ADD COLUMN etaSeconds INTEGER")
                db.execSQL("ALTER TABLE transfers ADD COLUMN lastProgressAt INTEGER")
                db.execSQL("ALTER TABLE transfers ADD COLUMN lastAttemptAt INTEGER")
                db.execSQL("ALTER TABLE transfers ADD COLUMN nextAttemptAt INTEGER")
                db.execSQL("ALTER TABLE transfers ADD COLUMN leaseOwner TEXT")
                db.execSQL("ALTER TABLE transfers ADD COLUMN leaseExpiresAt INTEGER")
                db.execSQL("ALTER TABLE transfers ADD COLUMN lastErrorCode TEXT")
                db.execSQL("ALTER TABLE transfers ADD COLUMN lastErrorMessage TEXT")
                db.execSQL("ALTER TABLE transfers ADD COLUMN lastHttpStatus INTEGER")
                db.execSQL("ALTER TABLE transfers ADD COLUMN correlationId TEXT")
                db.execSQL("""UPDATE transfers SET state = CASE status
                    WHEN 'RUNNING' THEN 'QUEUED'
                    WHEN 'PAUSED' THEN 'PAUSED'
                    WHEN 'COMPLETED' THEN 'COMPLETED'
                    WHEN 'CANCELLED' THEN 'CANCELLED'
                    WHEN 'FAILED' THEN 'FAILED_RETRYABLE'
                    ELSE 'QUEUED' END""")
                db.execSQL("UPDATE transfers SET status='PENDING', errorMessage='Recovered after app update', lastErrorCode='WORKER_INTERRUPTED', lastErrorMessage='Recovered after app update' WHERE status='RUNNING'")
                db.execSQL("UPDATE transfers SET operationType = direction WHERE direction IS NOT NULL")
                db.execSQL("UPDATE transfers SET remoteId = fileId WHERE remoteId IS NULL AND fileId IS NOT NULL")
                db.execSQL("UPDATE transfers SET localUri = localPath WHERE localUri IS NULL AND localPath IS NOT NULL")
                db.execSQL("UPDATE transfers SET parentFolderId = remotePath WHERE parentFolderId IS NULL AND remotePath IS NOT NULL")
                db.execSQL("UPDATE transfers SET providerType = (SELECT accountType FROM local_accounts WHERE local_accounts.id = transfers.accountId) WHERE providerType IS NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transfers_state_priority_createdAt ON transfers(state, priority, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transfers_leaseExpiresAt ON transfers(leaseExpiresAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transfers_providerType_remoteId ON transfers(providerType, remoteId)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE file_mutations ADD COLUMN providerType TEXT")
                db.execSQL("ALTER TABLE file_mutations ADD COLUMN remoteFileId TEXT")
                db.execSQL("ALTER TABLE file_mutations ADD COLUMN payloadJson TEXT")
                db.execSQL("ALTER TABLE file_mutations ADD COLUMN attemptCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE file_mutations ADD COLUMN nextAttemptAt INTEGER")
                db.execSQL("ALTER TABLE file_mutations ADD COLUMN lastErrorCode TEXT")
                db.execSQL("ALTER TABLE file_mutations ADD COLUMN lastErrorMessage TEXT")
                db.execSQL("ALTER TABLE file_mutations ADD COLUMN correlationId TEXT")
                db.execSQL("UPDATE file_mutations SET remoteFileId = fileId WHERE remoteFileId IS NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_file_mutations_status_nextAttemptAt_createdAt ON file_mutations(status, nextAttemptAt, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_file_mutations_accountId_remoteFileId_status ON file_mutations(accountId, remoteFileId, status)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folder_sync_meta ADD COLUMN providerType TEXT NOT NULL DEFAULT 'unknown'")
                db.execSQL("ALTER TABLE folder_sync_meta ADD COLUMN syncState TEXT NOT NULL DEFAULT 'IDLE'")
                db.execSQL("ALTER TABLE folder_sync_meta ADD COLUMN lastRequestedAt INTEGER")
                db.execSQL("ALTER TABLE folder_sync_meta ADD COLUMN lastSuccessfulAt INTEGER")
                db.execSQL("ALTER TABLE folder_sync_meta ADD COLUMN lastAttemptAt INTEGER")
                db.execSQL("ALTER TABLE folder_sync_meta ADD COLUMN nextAttemptAt INTEGER")
                db.execSQL("ALTER TABLE folder_sync_meta ADD COLUMN isCompleteSnapshot INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE folder_sync_meta ADD COLUMN continuationToken TEXT")
                db.execSQL("ALTER TABLE folder_sync_meta ADD COLUMN remoteRevision TEXT")
                db.execSQL("ALTER TABLE folder_sync_meta ADD COLUMN lastErrorCode TEXT")
                db.execSQL("ALTER TABLE folder_sync_meta ADD COLUMN lastErrorMessage TEXT")
                db.execSQL("UPDATE folder_sync_meta SET lastSuccessfulAt = lastSyncedAt WHERE lastSuccessfulAt IS NULL")
                db.execSQL("CREATE TABLE IF NOT EXISTS file_tombstones (tombstoneId TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, providerType TEXT NOT NULL, remoteId TEXT NOT NULL, folderId TEXT, deletedAt INTEGER NOT NULL, sourceRevision TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_file_tombstones_accountId_providerType_folderId ON file_tombstones(accountId, providerType, folderId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_file_tombstones_remoteId ON file_tombstones(remoteId)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS thumbnail_cache (
                        cacheKey TEXT NOT NULL PRIMARY KEY,
                        accountId TEXT NOT NULL,
                        providerType TEXT NOT NULL,
                        remoteId TEXT NOT NULL,
                        sourceRevision TEXT,
                        path TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        width INTEGER,
                        height INTEGER,
                        sizeBytes INTEGER NOT NULL,
                        isEncrypted INTEGER NOT NULL DEFAULT 0,
                        generatedAt INTEGER NOT NULL,
                        lastAccessAt INTEGER NOT NULL,
                        failureCount INTEGER NOT NULL DEFAULT 0,
                        nextRetryAt INTEGER
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_thumbnail_cache_accountId_lastAccessAt ON thumbnail_cache(accountId, lastAccessAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_thumbnail_cache_providerType_remoteId ON thumbnail_cache(providerType, remoteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_thumbnail_cache_lastAccessAt ON thumbnail_cache(lastAccessAt)")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cloud_files ADD COLUMN isEncrypted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cloud_files ADD COLUMN encryptionFormatVersion INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE cloud_files ADD COLUMN encryptionKeyId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE cloud_files ADD COLUMN encryptedMetadata TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE cloud_files ADD COLUMN encryptedSizeBytes INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE cloud_files ADD COLUMN encryptedChecksum TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE cloud_files ADD COLUMN opaqueRemoteName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE cloud_files ADD COLUMN plaintextSizeBytes INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE cloud_files ADD COLUMN originalMetadataAvailable INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE transfers ADD COLUMN isEncrypted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transfers ADD COLUMN encryptionFormatVersion INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE transfers ADD COLUMN encryptionKeyId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE transfers ADD COLUMN encryptedMetadata TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE transfers ADD COLUMN encryptedSizeBytes INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE transfers ADD COLUMN encryptedChecksum TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE transfers ADD COLUMN opaqueRemoteName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE transfers ADD COLUMN encryptedStagePath TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE transfers ADD COLUMN plaintextSizeBytes INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE transfers ADD COLUMN originalMetadataAvailable INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS backup_sources (
                        id TEXT NOT NULL PRIMARY KEY,
                        treeUri TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        presetType TEXT NOT NULL DEFAULT 'CUSTOM',
                        targetAccountId TEXT DEFAULT NULL,
                        targetFolderId TEXT DEFAULT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        permissionStatus TEXT NOT NULL DEFAULT 'UNKNOWN',
                        lastScanAt INTEGER NOT NULL DEFAULT 0,
                        lastBackupAt INTEGER NOT NULL DEFAULT 0,
                        lastSummary TEXT DEFAULT NULL,
                        lastError TEXT DEFAULT NULL,
                        lastQueuedCount INTEGER NOT NULL DEFAULT 0,
                        lastSkippedCount INTEGER NOT NULL DEFAULT 0,
                        lastFailedCount INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_backup_sources_treeUri ON backup_sources(treeUri)")
                db.execSQL("ALTER TABLE exclusions ADD COLUMN ruleType TEXT NOT NULL DEFAULT 'PATH_PATTERN'")
                db.execSQL("ALTER TABLE exclusions ADD COLUMN sizeLimitBytes INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE exclusions ADD COLUMN sourceUri TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE upload_records ADD COLUMN contentHash TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cloud_app_database"
                )
                    .addMigrations(
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
