package com.cloudyte.app

import android.content.Context
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.core.common.DefaultDispatcherProvider
import com.cloudyte.app.core.common.DispatcherProvider
import com.cloudyte.app.core.transfer.FileChunkStore
import com.cloudyte.app.core.transfer.ChunkStore
import com.cloudyte.app.core.transfer.RetryPolicy
import com.cloudyte.app.core.transfer.TransferCoordinator
import com.cloudyte.app.core.transfer.CloudTransferOperationExecutor
import com.cloudyte.app.core.transfer.RoomTransferQueueDrainer
import com.cloudyte.app.core.transfer.TransferScheduler
import com.cloudyte.app.core.transfer.ScheduleReason
import com.cloudyte.app.core.network.ConnectivityNetworkMonitor
import com.cloudyte.app.core.network.DefaultNetworkPolicy
import com.cloudyte.app.core.network.TransferNetworkGate
import com.cloudyte.app.core.cache.ThumbnailStore
import com.cloudyte.app.core.cache.PreviewStore
import com.cloudyte.app.core.cache.CacheQuotaManager
import com.cloudyte.app.core.transfer.TransferDiagnostics
import com.cloudyte.app.core.release.TransferRolloutGate
import com.cloudyte.app.data.CloudFileRepository
import com.cloudyte.app.data.TransferRepository
import com.cloudyte.app.data.repository.FileRepositoryImpl
import com.cloudyte.app.data.repository.AccountRepositoryImpl
import com.cloudyte.app.data.repository.TransferRepositoryImpl
import com.cloudyte.app.data.providers.ProviderRegistry
import com.cloudyte.app.db.AppDatabase
import com.cloudyte.app.domain.repository.FileRepository as DomainFileRepository
import com.cloudyte.app.domain.repository.AccountRepository as DomainAccountRepository
import com.cloudyte.app.domain.repository.TransferRepository as DomainTransferRepository
import com.cloudyte.app.domain.usecase.CancelTransfer
import com.cloudyte.app.domain.usecase.CreateFolder
import com.cloudyte.app.domain.usecase.DeleteFile
import com.cloudyte.app.domain.usecase.GetAccounts
import com.cloudyte.app.domain.usecase.ObserveFiles
import com.cloudyte.app.domain.usecase.PauseTransfer
import com.cloudyte.app.domain.usecase.RefreshFolder
import com.cloudyte.app.domain.usecase.RenameFile
import com.cloudyte.app.domain.usecase.ResumeTransfer
import com.cloudyte.app.domain.usecase.RetryTransfer
import com.cloudyte.app.sync.AutoSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import android.os.Process
import java.util.UUID

/**
 * Composition root for application-scoped dependencies.
 *
 * Feature code can gradually depend on this container while the existing
 * package structure is migrated toward core/data/domain/features boundaries.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val dispatchers: DispatcherProvider = DefaultDispatcherProvider
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatchers.default)
    val networkMonitor: ConnectivityNetworkMonitor = ConnectivityNetworkMonitor(appContext)
    val networkPolicy = DefaultNetworkPolicy()
    val transferNetworkGate = TransferNetworkGate(
        monitor = networkMonitor,
        policy = networkPolicy,
        preferences = { com.cloudyte.app.data.UserPreferences.observe(appContext).first() }
    )
    val retryPolicy: RetryPolicy = RetryPolicy()
    val chunkStore: ChunkStore by lazy { FileChunkStore(appContext) }

    val providerRegistry: ProviderRegistry by lazy {
        ProviderRegistry(ApiClient.apiService)
    }

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(appContext)
    }

    val contentKeyManager: com.cloudyte.app.core.security.ContentKeyManager by lazy {
        com.cloudyte.app.core.security.ContentKeyManager(appContext)
    }

    val streamFileCrypto: com.cloudyte.app.core.security.StreamFileCrypto by lazy {
        com.cloudyte.app.core.security.StreamFileCrypto()
    }

    val metadataCodec: com.cloudyte.app.core.security.EncryptedMetadataCodec by lazy {
        com.cloudyte.app.core.security.EncryptedMetadataCodec()
    }

    val keyBackupCodec: com.cloudyte.app.core.security.KeyBackupCodec by lazy {
        com.cloudyte.app.core.security.KeyBackupCodec(contentKeyManager)
    }

    val thumbnailStore: ThumbnailStore by lazy {
        ThumbnailStore(appContext, database.thumbnailDao(), contentKeyManager, streamFileCrypto)
    }

    val previewStore: PreviewStore by lazy { PreviewStore(appContext) }

    val cacheQuotaManager: CacheQuotaManager by lazy {
        CacheQuotaManager(appContext, database.thumbnailDao())
    }

    val cloudFileRepository: CloudFileRepository by lazy {
        CloudFileRepository(
            db = database,
            api = ApiClient.apiService,
            contentKeyProvider = contentKeyManager,
            metadataCodec = metadataCodec
        )
    }

    val transferRepository: TransferRepository by lazy {
        TransferRepository(database.transferDao(), providerRegistry)
    }

    val transferScheduler: TransferScheduler by lazy { TransferScheduler(appContext) }

    val transferDiagnostics: TransferDiagnostics by lazy { TransferDiagnostics() }

    /** Fail-safe kill switch for staged production rollout; queued rows remain durable when disabled. */
    val transferRolloutGate: TransferRolloutGate by lazy { TransferRolloutGate(appContext) }

    val transferQueueDrainer by lazy {
        RoomTransferQueueDrainer(
            queueDao = database.transferQueueDao(),
            executor = CloudTransferOperationExecutor(
                context = appContext,
                database = database,
                transfers = transferRepository,
                providers = providerRegistry,
                contentKeyProvider = contentKeyManager,
                streamFileCrypto = streamFileCrypto,
                metadataCodec = metadataCodec,
                onLocalDeletionFailed = { transfer, message ->
                    com.cloudyte.app.core.transfer.notification.NotificationHelper.postEventIfEnabled(
                        context = appContext,
                        eventType = com.cloudyte.app.core.transfer.notification.NotificationEventType.BACKUP_FAILURE,
                        title = "Local copy not deleted",
                        message = message,
                        notificationId = transfer.id.hashCode()
                    )
                }
            ),
            ownerId = "${Process.myPid()}-${UUID.randomUUID()}",
            networkGate = transferNetworkGate,
            diagnostics = transferDiagnostics,
            maxRetryCount = {
                com.cloudyte.app.data.UserPreferences.observe(appContext).first().transferRetryCount
            },
            onTerminalOutcome = { transfer, outcome ->
                when (outcome) {
                    com.cloudyte.app.core.transfer.TransferOutcome.Completed -> {
                        val eventType = if (transfer.direction == com.cloudyte.app.db.TransferDirection.BACKUP.name) {
                            com.cloudyte.app.core.transfer.notification.NotificationEventType.BACKUP_COMPLETED
                        } else {
                            com.cloudyte.app.core.transfer.notification.NotificationEventType.TRANSFER_COMPLETED
                        }
                        com.cloudyte.app.core.transfer.notification.NotificationHelper.postEventIfEnabled(
                            context = appContext,
                            eventType = eventType,
                            title = if (transfer.direction == com.cloudyte.app.db.TransferDirection.DOWNLOAD.name) "Download complete" else "Upload complete",
                            message = transfer.fileName,
                            notificationId = transfer.id.hashCode()
                        )
                    }
                    is com.cloudyte.app.core.transfer.TransferOutcome.Permanent -> {
                        val eventType = if (transfer.direction == com.cloudyte.app.db.TransferDirection.BACKUP.name) {
                            com.cloudyte.app.core.transfer.notification.NotificationEventType.BACKUP_FAILURE
                        } else {
                            com.cloudyte.app.core.transfer.notification.NotificationEventType.TRANSFER_FAILURE
                        }
                        com.cloudyte.app.core.transfer.notification.NotificationHelper.postEventIfEnabled(
                            context = appContext,
                            eventType = eventType,
                            title = "Transfer failed: ${transfer.fileName}",
                            message = outcome.message,
                            notificationId = transfer.id.hashCode()
                        )
                    }
                    is com.cloudyte.app.core.transfer.TransferOutcome.WaitingForAuth -> {
                        com.cloudyte.app.core.transfer.notification.NotificationHelper.postEventIfEnabled(
                            context = appContext,
                            eventType = com.cloudyte.app.core.transfer.notification.NotificationEventType.TRANSFER_FAILURE,
                            title = "Authentication required",
                            message = "${transfer.fileName}: ${outcome.message}",
                            notificationId = transfer.id.hashCode()
                        )
                    }
                    else -> Unit
                }
            }
        )
    }

    val fileDomainRepository: DomainFileRepository by lazy {
        FileRepositoryImpl(cloudFileRepository)
    }

    val transferDomainRepository: DomainTransferRepository by lazy {
        TransferRepositoryImpl(transferRepository)
    }

    val transferCoordinator: TransferCoordinator by lazy {
        TransferCoordinator(
            context = appContext,
            repository = transferDomainRepository,
            scheduleBackgroundWork = { context ->
                transferScheduler.kick(ScheduleReason.USER_ENQUEUE, allowMetered = true)
            },
            diagnostics = transferDiagnostics,
            scheduler = transferScheduler
        )
    }

    val accountDomainRepository: DomainAccountRepository by lazy {
        AccountRepositoryImpl(ApiClient.apiService)
    }
    val getAccounts by lazy { GetAccounts(accountDomainRepository) }

    val observeFiles by lazy { ObserveFiles(fileDomainRepository) }
    val refreshFolder by lazy { RefreshFolder(fileDomainRepository) }
    val renameFile by lazy { RenameFile(fileDomainRepository) }
    val deleteFile by lazy { DeleteFile(fileDomainRepository) }
    val createFolder by lazy { CreateFolder(fileDomainRepository) }
    val retryTransfer by lazy { RetryTransfer(transferDomainRepository) }
    val pauseTransfer by lazy { PauseTransfer(transferDomainRepository) }
    val resumeTransfer by lazy { ResumeTransfer(transferDomainRepository) }
    val cancelTransfer by lazy { CancelTransfer(transferDomainRepository) }

    init {
        networkMonitor.start()
        applicationScope.launch {
            runCatching {
                val activeIds = database.transferDao().observeActiveTransfers().first().map { it.id }.toSet()
                com.cloudyte.app.core.security.SecureFileCleanup.cleanupStaleTempFiles(appContext, activeIds)
            }
        }
        applicationScope.launch {
            networkMonitor.status
                .distinctUntilChanged()
                .drop(1)
                .collect { status ->
                    if (status != com.cloudyte.app.core.network.NetworkStatus.UNAVAILABLE &&
                        status != com.cloudyte.app.core.network.NetworkStatus.CAPTIVE_PORTAL) {
                        transferScheduler.kick(ScheduleReason.NETWORK_RESTORED, allowMetered = true)
                    }
                }
        }
    }
}
