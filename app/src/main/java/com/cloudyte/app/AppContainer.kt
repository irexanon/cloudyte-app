package com.cloudyte.app

import android.content.Context
import com.cloudyte.app.api.ApiClient
import com.cloudyte.app.core.common.DefaultDispatcherProvider
import com.cloudyte.app.core.common.DispatcherProvider
import com.cloudyte.app.core.transfer.FileChunkStore
import com.cloudyte.app.core.transfer.ChunkStore
import com.cloudyte.app.core.transfer.RetryPolicy
import com.cloudyte.app.core.transfer.TransferCoordinator
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
    val retryPolicy: RetryPolicy = RetryPolicy()
    val chunkStore: ChunkStore by lazy { FileChunkStore(appContext) }

    val providerRegistry: ProviderRegistry by lazy {
        ProviderRegistry(ApiClient.apiService)
    }

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(appContext)
    }

    val cloudFileRepository: CloudFileRepository by lazy {
        CloudFileRepository(database, ApiClient.apiService)
    }

    val transferRepository: TransferRepository by lazy {
        TransferRepository(database.transferDao())
    }

    val fileDomainRepository: DomainFileRepository by lazy {
        FileRepositoryImpl(cloudFileRepository)
    }

    val transferDomainRepository: DomainTransferRepository by lazy {
        TransferRepositoryImpl(transferRepository)
    }

    val transferCoordinator: TransferCoordinator by lazy {
        TransferCoordinator(appContext, transferDomainRepository) { context ->
            AutoSyncManager.triggerImmediateSync(context, isManual = true)
        }
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
}
