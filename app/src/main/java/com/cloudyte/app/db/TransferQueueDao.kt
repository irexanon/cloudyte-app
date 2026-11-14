package com.cloudyte.app.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.cloudyte.app.core.transfer.TransferExecutionLease

@Dao
abstract class TransferQueueDao {
    @Query("SELECT * FROM transfers WHERE state IN ('QUEUED','FAILED_RETRYABLE') AND (nextAttemptAt IS NULL OR nextAttemptAt <= :now) AND (leaseOwner IS NULL OR leaseExpiresAt IS NULL OR leaseExpiresAt <= :now) ORDER BY priority DESC, createdAt ASC LIMIT 1")
    protected abstract suspend fun nextCandidate(now: Long): LocalTransfer?

    @Query("UPDATE transfers SET state='RUNNING', status='RUNNING', leaseOwner=:ownerId, leaseExpiresAt=:leaseUntil, lastAttemptAt=:now, attemptCount=attemptCount+1, errorMessage=NULL, lastErrorCode=NULL, lastErrorMessage=NULL, lastHttpStatus=NULL, updatedAt=:now WHERE id=:id AND state IN ('QUEUED','FAILED_RETRYABLE') AND (nextAttemptAt IS NULL OR nextAttemptAt <= :now) AND (leaseOwner IS NULL OR leaseExpiresAt IS NULL OR leaseExpiresAt <= :now)")
    protected abstract suspend fun claim(id: String, ownerId: String, now: Long, leaseUntil: Long): Int

    @Query("SELECT * FROM transfers WHERE id=:id")
    protected abstract suspend fun row(id: String): LocalTransfer?

    open suspend fun currentRow(id: String): LocalTransfer? = row(id)

    @Transaction
    open suspend fun acquireNextRunnableLease(ownerId: String, now: Long, leaseUntil: Long): LocalTransfer? {
        repeat(4) {
            val candidate = nextCandidate(now) ?: return null
            if (claim(candidate.id, ownerId, now, leaseUntil) == 1) return row(candidate.id)
        }
        return null
    }

    @Query("UPDATE transfers SET leaseExpiresAt=:leaseUntil, updatedAt=:now WHERE id=:id AND leaseOwner=:ownerId AND state='RUNNING' AND leaseExpiresAt > :now")
    abstract suspend fun renewLease(id: String, ownerId: String, leaseUntil: Long, now: Long): Int

    @Query("UPDATE transfers SET leaseOwner=NULL, leaseExpiresAt=NULL, updatedAt=:now WHERE id=:id AND leaseOwner=:ownerId")
    abstract suspend fun releaseLease(id: String, ownerId: String, now: Long): Int

    @Query("UPDATE transfers SET bytesTransferred=:bytes, totalBytes=:total, bytesPerSecond=:speed, etaSeconds=:eta, lastProgressAt=:now, updatedAt=:now WHERE id=:id AND leaseOwner=:ownerId AND state='RUNNING' AND leaseExpiresAt > :now")
    abstract suspend fun persistProgress(id: String, ownerId: String, bytes: Long, total: Long, speed: Long, eta: Long?, now: Long): Int

    @Query("UPDATE transfers SET state='QUEUED', status='PENDING', leaseOwner=NULL, leaseExpiresAt=NULL, updatedAt=:now WHERE id=:id AND leaseOwner=:ownerId AND state='RUNNING'")
    abstract suspend fun returnToQueue(id: String, ownerId: String, now: Long): Int

    @Query("UPDATE transfers SET state=:nextState, status=:legacyStatus, lastErrorCode=:errorCode, lastErrorMessage=:errorMessage, lastHttpStatus=:httpStatus, errorMessage=:errorMessage, nextAttemptAt=:nextAttemptAt, leaseOwner=CASE WHEN :keepLease THEN leaseOwner ELSE NULL END, leaseExpiresAt=CASE WHEN :keepLease THEN leaseExpiresAt ELSE NULL END, updatedAt=:now WHERE id=:id AND state=:expectedState AND leaseOwner=:ownerId AND leaseExpiresAt > :now")
    abstract suspend fun transitionOwned(id: String, ownerId: String, expectedState: String, nextState: String, legacyStatus: String, errorCode: String?, errorMessage: String?, nextAttemptAt: Long?, httpStatus: Int?, keepLease: Boolean, now: Long): Int

    @Query("UPDATE transfers SET state=:finalState, status=:legacyStatus, lastErrorCode=:errorCode, lastErrorMessage=:errorMessage, errorMessage=:errorMessage, leaseOwner=NULL, leaseExpiresAt=NULL, updatedAt=:now WHERE id=:id AND (leaseOwner=:ownerId OR leaseOwner IS NULL)")
    abstract suspend fun finalizeInterrupted(id: String, ownerId: String, finalState: String, legacyStatus: String, errorCode: String?, errorMessage: String?, now: Long): Int

    @Query("UPDATE transfers SET state='QUEUED', status='PENDING', leaseOwner=NULL, leaseExpiresAt=NULL, lastErrorCode='WORKER_INTERRUPTED', lastErrorMessage='Recovered after execution lease expired', errorMessage='Recovered after execution lease expired', updatedAt=:now WHERE state='RUNNING' AND leaseExpiresAt IS NOT NULL AND leaseExpiresAt <= :now")
    abstract suspend fun recoverExpiredLeases(now: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM transfers WHERE state IN ('QUEUED','FAILED_RETRYABLE') AND (nextAttemptAt IS NULL OR nextAttemptAt <= :now))")
    abstract suspend fun hasRunnableTransfers(now: Long): Boolean

    @Query("UPDATE transfers SET state='QUEUED', status='PENDING', updatedAt=:now WHERE state='WAITING_FOR_NETWORK'")
    abstract suspend fun requeueNetworkWaiters(now: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM transfers WHERE state='WAITING_FOR_NETWORK')")
    abstract suspend fun hasNetworkWaiters(): Boolean

    @Query("SELECT MIN(nextAttemptAt) FROM transfers WHERE state='FAILED_RETRYABLE' AND nextAttemptAt > :now")
    abstract suspend fun nextRetryAt(now: Long): Long?

    @Query("SELECT MIN(leaseExpiresAt) FROM transfers WHERE state='RUNNING' AND leaseExpiresAt > :now")
    abstract suspend fun nextLeaseExpiry(now: Long): Long?

    @Query("SELECT COALESCE(SUM(bytesTransferred), 0) FROM transfers WHERE state IN ('QUEUED','RUNNING','WAITING_FOR_NETWORK','WAITING_FOR_AUTH')")
    abstract suspend fun aggregateBytesTransferred(): Long

    @Query("SELECT COALESCE(SUM(totalBytes), 0) FROM transfers WHERE state IN ('QUEUED','RUNNING','WAITING_FOR_NETWORK','WAITING_FOR_AUTH')")
    abstract suspend fun aggregateTotalBytes(): Long

    fun leaseFor(row: LocalTransfer, ownerId: String, acquiredAt: Long): TransferExecutionLease? {
        val expiresAt = row.leaseExpiresAt ?: return null
        if (row.leaseOwner != ownerId) return null
        return TransferExecutionLease(row.id, ownerId, acquiredAt, expiresAt)
    }
}
