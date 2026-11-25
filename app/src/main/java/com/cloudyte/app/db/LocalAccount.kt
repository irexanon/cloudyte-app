package com.cloudyte.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cloudyte.app.api.Account

@Entity(tableName = "local_accounts")
data class LocalAccount(
    @PrimaryKey val id: String,
    val name: String,
    val driveName: String?,
    val email: String?,
    val accountType: String,
    val usageBytes: Long,
    val totalBytes: Long,
    val percentage: Float,
    val fileCount: Int,
    val isDefault: Boolean,
    val channelId: String?,
    val cachedAt: Long = System.currentTimeMillis(),
    val error: String? = null,
    val errorMessage: String? = null,
    val errorCode: String? = null,
    val isForum: Boolean = false,
    val chatType: String? = null
) {
    fun toAccount(): Account {
        return Account(
            id = this.id,
            name = this.name,
            driveName = this.driveName,
            email = this.email,
            accountType = this.accountType,
            usage = this.usageBytes,
            total = this.totalBytes,
            percentage = this.percentage,
            fileCount = this.fileCount,
            isDefault = this.isDefault,
            channelId = this.channelId,
            error = this.error,
            errorMessage = this.errorMessage,
            errorCode = this.errorCode,
            isForum = this.isForum,
            chatType = this.chatType
        )
    }

    companion object {
        fun fromAccount(account: Account): LocalAccount {
            return LocalAccount(
                id = account.effectiveId,
                name = account.effectiveName,
                driveName = account.driveName,
                email = account.email,
                accountType = account.effectiveType,
                usageBytes = account.usageBytes,
                totalBytes = account.totalBytes,
                percentage = account.effectivePercentage,
                fileCount = account.fileCount ?: 0,
                isDefault = account.isDefault == true,
                channelId = account.channelId,
                error = account.errorAsString,
                errorMessage = account.errorMessage,
                errorCode = account.errorCodeString,
                isForum = account.isForum == true,
                chatType = account.chatType
            )
        }
    }
}
