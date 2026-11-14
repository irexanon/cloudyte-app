package com.cloudyte.app.core.transfer

import com.cloudyte.app.db.LocalTransfer
import com.cloudyte.app.db.TransferQueueDao
import com.cloudyte.app.core.network.PolicyDecision
import com.cloudyte.app.core.network.TransferNetworkGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class TransferInterruptedException(
    val outcome: TransferOutcome,
    message: String = "Transfer interrupted with $outcome"
) : CancellationException(message)

interface TransferOperationExecutor {
    suspend fun execute(transfer: LocalTransfer, leaseOwner: String, onProgress: suspend (bytes: Long, total: Long, speed: Long, etaSeconds: Long?) -> Unit): TransferOutcome
    suspend fun onCompleted(transfer: LocalTransfer) = Unit
    suspend fun onCancelled(transfer: LocalTransfer) = Unit
}

sealed interface TransferOutcome {
    data object Completed : TransferOutcome
    data class Retryable(val code: String, val message: String, val retryAt: Long? = null, val httpStatus: Int? = null) : TransferOutcome
    data class Permanent(val code: String, val message: String, val httpStatus: Int? = null) : TransferOutcome
    data class WaitingForNetwork(val code: String, val message: String) : TransferOutcome
    data class WaitingForAuth(val code: String, val message: String) : TransferOutcome
    data object Paused : TransferOutcome
    data object Cancelled : TransferOutcome
}

enum class DrainTrigger { USER_REQUEST, USER_ENQUEUE, USER_RETRY, NETWORK_RESTORED, APP_START, RETRY, PERIODIC_MAINTENANCE }
sealed interface DrainResult {
    data class Drained(val processed: Int, val hasMore: Boolean) : DrainResult
    data object Stopped : DrainResult
}

interface TransferQueueDrainer {
    suspend fun drain(trigger: DrainTrigger, isStopped: () -> Boolean, onProgress: suspend (TransferProgressSnapshot) -> Unit): DrainResult
}

/** Bounded queue consumer. A stale owner is fenced from writing progress or completion. */
class RoomTransferQueueDrainer(
    private val queueDao: TransferQueueDao,
    private val executor: TransferOperationExecutor,
    private val ownerId: String,
    private val now: () -> Long = System::currentTimeMillis,
    private val leaseDurationMs: Long = 2 * 60 * 1000L,
    private val maxTransfersPerDrain: Int = 3,
    private val networkGate: TransferNetworkGate? = null,
    private val diagnostics: TransferDiagnostics? = null,
    private val maxRetryCount: suspend () -> Int = { Int.MAX_VALUE },
    private val onTerminalOutcome: (suspend (LocalTransfer, TransferOutcome) -> Unit)? = null
) : TransferQueueDrainer {
    override suspend fun drain(
        trigger: DrainTrigger,
        isStopped: () -> Boolean,
        onProgress: suspend (TransferProgressSnapshot) -> Unit
    ): DrainResult {
        var processed = 0
        queueDao.recoverExpiredLeases(now())
        while (processed < maxTransfersPerDrain && !isStopped()) {
            val acquiredAt = now()
            val leaseOwner = "$ownerId:${UUID.randomUUID()}"
            val transfer = queueDao.acquireNextRunnableLease(leaseOwner, acquiredAt, acquiredAt + leaseDurationMs) ?: break
            try {
                val preExecutionInterrupt = detectInterruption(transfer.id)
                if (preExecutionInterrupt != null) {
                    applyOutcome(transfer, leaseOwner, preExecutionInterrupt)
                    processed++
                    continue
                }
                when (val decision = networkGate?.check(transfer)) {
                    is PolicyDecision.WaitingForNetwork,
                    is PolicyDecision.WaitingForCharging,
                    is PolicyDecision.WaitingForBattery,
                    is PolicyDecision.WaitingForRoaming -> {
                        val reason = when (decision) {
                            is PolicyDecision.WaitingForNetwork -> decision.reason
                            is PolicyDecision.WaitingForCharging -> decision.reason
                            is PolicyDecision.WaitingForBattery -> decision.reason
                            is PolicyDecision.WaitingForRoaming -> decision.reason
                            else -> "NETWORK_POLICY"
                        }
                        transition(transfer, leaseOwner, TransferState.RUNNING, TransferState.WAITING_FOR_NETWORK, "PENDING", "NETWORK_POLICY", reason)
                        diagnostics?.recordEvent(
                            operationType = "queue_policy",
                            transferId = transfer.id,
                            accountId = transfer.accountId,
                            providerType = transfer.providerType,
                            state = TransferState.WAITING_FOR_NETWORK.name,
                            retryCount = transfer.attemptCount,
                            errorCode = "NETWORK_POLICY",
                            networkState = reason,
                            workerRunId = leaseOwner
                        )
                        processed++
                        continue
                    }
                    is PolicyDecision.WaitingForAuth -> {
                        transition(transfer, leaseOwner, TransferState.RUNNING, TransferState.WAITING_FOR_AUTH, "PENDING", "AUTH_REQUIRED", decision.reason)
                        diagnostics?.recordEvent(
                            operationType = "queue_policy",
                            transferId = transfer.id,
                            accountId = transfer.accountId,
                            providerType = transfer.providerType,
                            state = TransferState.WAITING_FOR_AUTH.name,
                            retryCount = transfer.attemptCount,
                            errorCode = "AUTH_REQUIRED",
                            workerRunId = leaseOwner
                        )
                        processed++
                        continue
                    }
                    PolicyDecision.Allowed, null -> Unit
                }
                val outcome = coroutineScope {
                    val executionJob = coroutineContext[Job]
                    ActiveTransferSignalRegistry.registerHook(transfer.id) { signal ->
                        val interruptedOutcome = when (signal) {
                            ActiveTransferSignal.CANCEL -> TransferOutcome.Cancelled
                            ActiveTransferSignal.PAUSE -> TransferOutcome.Paused
                        }
                        executionJob?.cancel(TransferInterruptedException(interruptedOutcome))
                    }
                    val heartbeat = launch {
                        while (true) {
                            delay((leaseDurationMs / 3L).coerceAtLeast(5_000L))
                            detectInterruption(transfer.id)?.let { throw TransferInterruptedException(it) }
                            val tick = now()
                            if (queueDao.renewLease(transfer.id, leaseOwner, tick + leaseDurationMs, tick) != 1) {
                                detectInterruption(transfer.id)?.let { throw TransferInterruptedException(it) }
                                throw CancellationException("Transfer lease lost")
                            }
                        }
                    }
                    try {
                        executor.execute(transfer, leaseOwner) { bytes, total, speed, eta ->
                            detectInterruption(transfer.id)?.let { throw TransferInterruptedException(it) }
                            val tick = now()
                            if (queueDao.renewLease(transfer.id, leaseOwner, tick + leaseDurationMs, tick) != 1) {
                                detectInterruption(transfer.id)?.let { throw TransferInterruptedException(it) }
                                error("Transfer lease lost")
                            }
                            if (queueDao.persistProgress(transfer.id, leaseOwner, bytes, total, speed, eta, tick) != 1) {
                                detectInterruption(transfer.id)?.let { throw TransferInterruptedException(it) }
                                error("Transfer progress write lost its lease")
                            }
                            onProgress(TransferProgressSnapshot(transfer.id, TransferState.RUNNING, bytes, total, speed, eta, retryCount = transfer.attemptCount))
                        }
                    } finally {
                        ActiveTransferSignalRegistry.unregisterHook(transfer.id)
                        heartbeat.cancelAndJoin()
                    }
                }
                val resolvedOutcome = detectInterruption(transfer.id) ?: outcome
                applyOutcome(transfer, leaseOwner, resolvedOutcome)
                processed++
            } catch (interrupted: TransferInterruptedException) {
                applyOutcome(transfer, leaseOwner, interrupted.outcome)
                processed++
            } catch (cancelled: CancellationException) {
                val interrupted = detectInterruption(transfer.id)
                if (interrupted != null && !isStopped()) {
                    applyOutcome(transfer, leaseOwner, interrupted)
                    processed++
                } else {
                    queueDao.returnToQueue(transfer.id, leaseOwner, now())
                    throw cancelled
                }
            } catch (error: Exception) {
                val interrupted = detectInterruption(transfer.id)
                if (interrupted != null) {
                    applyOutcome(transfer, leaseOwner, interrupted)
                } else {
                    runCatching {
                        applyOutcome(
                            transfer,
                            leaseOwner,
                            TransferOutcome.Retryable("UNEXPECTED", error.message ?: "Transfer failed", now() + 30_000L)
                        )
                    }
                }
                processed++
            } finally {
                ActiveTransferSignalRegistry.unregisterHook(transfer.id)
                ActiveTransferSignalRegistry.clear(transfer.id)
                queueDao.releaseLease(transfer.id, leaseOwner, now())
            }
        }
        return if (isStopped()) DrainResult.Stopped else DrainResult.Drained(processed, queueDao.hasRunnableTransfers(now()))
    }

    private suspend fun detectInterruption(transferId: String): TransferOutcome? {
        when (ActiveTransferSignalRegistry.currentSignal(transferId)) {
            ActiveTransferSignal.CANCEL -> return TransferOutcome.Cancelled
            ActiveTransferSignal.PAUSE -> return TransferOutcome.Paused
            null -> Unit
        }
        val row = queueDao.currentRow(transferId) ?: return null
        return when {
            row.state in setOf(TransferState.CANCELLING.name, TransferState.CANCELLED.name) ||
                row.status == "CANCELLED" -> TransferOutcome.Cancelled
            row.state in setOf(TransferState.PAUSING.name, TransferState.PAUSED.name) ||
                row.status == "PAUSED" -> TransferOutcome.Paused
            else -> null
        }
    }

    private suspend fun applyOutcome(
        transfer: LocalTransfer,
        leaseOwner: String,
        outcome: TransferOutcome
    ) {
        var effectiveOutcome = outcome
        when (outcome) {
            TransferOutcome.Completed -> {
                transition(transfer, leaseOwner, TransferState.RUNNING, TransferState.COMPLETED, "COMPLETED")
                runCatching { executor.onCompleted(transfer) }
                runCatching { onTerminalOutcome?.invoke(transfer, outcome) }
            }
            is TransferOutcome.Retryable -> {
                val retryLimit = runCatching { maxRetryCount() }.getOrDefault(Int.MAX_VALUE).coerceAtLeast(0)
                if (transfer.attemptCount > retryLimit) {
                    val permanentMessage = "Retry limit ($retryLimit) exceeded: ${outcome.message}"
                    val permanent = TransferOutcome.Permanent("RETRY_LIMIT_EXCEEDED", permanentMessage, outcome.httpStatus)
                    effectiveOutcome = permanent
                    transition(
                        transfer,
                        leaseOwner,
                        TransferState.RUNNING,
                        TransferState.FAILED_PERMANENT,
                        "FAILED",
                        permanent.code,
                        permanent.message,
                        httpStatus = permanent.httpStatus
                    )
                    runCatching { onTerminalOutcome?.invoke(transfer, permanent) }
                } else {
                    transition(
                        transfer,
                        leaseOwner,
                        TransferState.RUNNING,
                        TransferState.FAILED_RETRYABLE,
                        "FAILED",
                        outcome.code,
                        outcome.message,
                        outcome.retryAt ?: retryAt(transfer.attemptCount),
                        outcome.httpStatus
                    )
                }
            }
            is TransferOutcome.Permanent -> {
                transition(transfer, leaseOwner, TransferState.RUNNING, TransferState.FAILED_PERMANENT, "FAILED", outcome.code, outcome.message, httpStatus = outcome.httpStatus)
                runCatching { onTerminalOutcome?.invoke(transfer, outcome) }
            }
            is TransferOutcome.WaitingForNetwork -> transition(transfer, leaseOwner, TransferState.RUNNING, TransferState.WAITING_FOR_NETWORK, "PENDING", outcome.code, outcome.message)
            is TransferOutcome.WaitingForAuth -> {
                transition(transfer, leaseOwner, TransferState.RUNNING, TransferState.WAITING_FOR_AUTH, "PENDING", outcome.code, outcome.message)
                runCatching { onTerminalOutcome?.invoke(transfer, outcome) }
            }
            TransferOutcome.Paused -> transitionPausedDeterministic(transfer, leaseOwner)
            TransferOutcome.Cancelled -> {
                val latest = queueDao.currentRow(transfer.id) ?: transfer
                runCatching { executor.onCancelled(latest) }
                transitionCancelledDeterministic(transfer, leaseOwner)
            }
        }
        diagnostics?.recordEvent(
            operationType = "transfer",
            transferId = transfer.id,
            accountId = transfer.accountId,
            providerType = transfer.providerType,
            state = effectiveOutcome.diagnosticState(),
            retryCount = transfer.attemptCount,
            errorCode = effectiveOutcome.diagnosticCode(),
            httpStatus = effectiveOutcome.diagnosticHttpStatus(),
            checkpointAt = now(),
            workerRunId = leaseOwner
        )
    }

    private suspend fun transitionPausedDeterministic(transfer: LocalTransfer, leaseOwner: String) {
        val current = queueDao.currentRow(transfer.id)
        if (current != null && current.state == TransferState.RUNNING.name && current.leaseOwner == leaseOwner) {
            transitionVia(transfer, leaseOwner, TransferState.RUNNING, TransferState.PAUSING, TransferState.PAUSED, "PAUSED")
        } else {
            queueDao.finalizeInterrupted(
                id = transfer.id,
                ownerId = leaseOwner,
                finalState = TransferState.PAUSED.name,
                legacyStatus = "PAUSED",
                errorCode = null,
                errorMessage = null,
                now = now()
            )
        }
    }

    private suspend fun transitionCancelledDeterministic(transfer: LocalTransfer, leaseOwner: String) {
        val current = queueDao.currentRow(transfer.id)
        if (current != null && current.state == TransferState.RUNNING.name && current.leaseOwner == leaseOwner) {
            transitionVia(transfer, leaseOwner, TransferState.RUNNING, TransferState.CANCELLING, TransferState.CANCELLED, "CANCELLED")
        } else {
            queueDao.finalizeInterrupted(
                id = transfer.id,
                ownerId = leaseOwner,
                finalState = TransferState.CANCELLED.name,
                legacyStatus = "CANCELLED",
                errorCode = "USER_CANCELLED",
                errorMessage = "Cancelled",
                now = now()
            )
        }
    }

    private suspend fun transition(
        transfer: LocalTransfer,
        leaseOwner: String,
        expected: TransferState,
        next: TransferState,
        legacyStatus: String,
        errorCode: String? = null,
        errorMessage: String? = null,
        nextAttemptAt: Long? = null,
        httpStatus: Int? = null
    ) {
        require(expected.canTransitionTo(next)) { "Illegal transfer transition $expected -> $next for ${transfer.id}" }
        check(queueDao.transitionOwned(transfer.id, leaseOwner, expected.name, next.name, legacyStatus, errorCode, errorMessage, nextAttemptAt, httpStatus, false, now()) == 1) {
            "Transfer lease lost before state transition"
        }
    }

    private suspend fun transitionVia(transfer: LocalTransfer, leaseOwner: String, from: TransferState, intermediate: TransferState, final: TransferState, legacyStatus: String) {
        require(from.canTransitionTo(intermediate) && intermediate.canTransitionTo(final))
        check(queueDao.transitionOwned(transfer.id, leaseOwner, from.name, intermediate.name, legacyStatus, null, null, null, null, true, now()) == 1)
        check(queueDao.transitionOwned(transfer.id, leaseOwner, intermediate.name, final.name, legacyStatus, null, null, null, null, false, now()) == 1)
    }

    private fun retryAt(attemptCount: Int): Long {
        val exponent = (attemptCount - 1).coerceIn(0, 10)
        val delayMs = (30_000L * (1L shl exponent)).coerceAtMost(6 * 60 * 60 * 1000L)
        return now() + delayMs
    }

    private fun TransferOutcome.diagnosticState(): String = when (this) {
        TransferOutcome.Completed -> TransferState.COMPLETED.name
        is TransferOutcome.Retryable -> TransferState.FAILED_RETRYABLE.name
        is TransferOutcome.Permanent -> TransferState.FAILED_PERMANENT.name
        is TransferOutcome.WaitingForNetwork -> TransferState.WAITING_FOR_NETWORK.name
        is TransferOutcome.WaitingForAuth -> TransferState.WAITING_FOR_AUTH.name
        TransferOutcome.Paused -> TransferState.PAUSED.name
        TransferOutcome.Cancelled -> TransferState.CANCELLED.name
    }

    private fun TransferOutcome.diagnosticCode(): String? = when (this) {
        is TransferOutcome.Retryable -> code
        is TransferOutcome.Permanent -> code
        is TransferOutcome.WaitingForNetwork -> code
        is TransferOutcome.WaitingForAuth -> code
        else -> null
    }

    private fun TransferOutcome.diagnosticHttpStatus(): Int? = when (this) {
        is TransferOutcome.Retryable -> httpStatus
        is TransferOutcome.Permanent -> httpStatus
        else -> null
    }
}

