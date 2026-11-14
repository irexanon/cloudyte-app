package com.cloudyte.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import com.cloudyte.app.data.UserPreferencesState
import com.cloudyte.app.db.LocalTransfer
import kotlinx.coroutines.flow.Flow

/** Provider-independent connectivity state used by workers and scheduling decisions. */
enum class NetworkStatus {
    UNAVAILABLE,
    VALIDATED_METERED,
    VALIDATED_UNMETERED,
    CAPTIVE_PORTAL
}

data class NetworkPolicySnapshot(
    val status: NetworkStatus,
    val allowMeteredTransfers: Boolean,
    val allowRoaming: Boolean,
    val requireChargingForBackup: Boolean,
    val batterySaverActive: Boolean,
    val isRoaming: Boolean = false,
    val isCharging: Boolean = false,
    val batteryNotLow: Boolean = true,
    val requireWifiForBackup: Boolean = !allowMeteredTransfers
)

interface NetworkMonitor {
    val status: Flow<NetworkStatus>
    fun current(): NetworkStatus
    fun snapshot(preferences: UserPreferencesState): NetworkPolicySnapshot
}

sealed interface PolicyDecision {
    data object Allowed : PolicyDecision
    data class WaitingForNetwork(val reason: String) : PolicyDecision
    data class WaitingForCharging(val reason: String) : PolicyDecision
    data class WaitingForBattery(val reason: String) : PolicyDecision
    data class WaitingForRoaming(val reason: String) : PolicyDecision
    data class WaitingForAuth(val reason: String) : PolicyDecision
}

/** Central policy: browsing remains cache-first, while durable transfers are gated here. */
interface NetworkPolicy {
    fun canStart(transfer: LocalTransfer, snapshot: NetworkPolicySnapshot): PolicyDecision
}

class DefaultNetworkPolicy : NetworkPolicy {
    override fun canStart(transfer: LocalTransfer, snapshot: NetworkPolicySnapshot): PolicyDecision {
        if (snapshot.status == NetworkStatus.UNAVAILABLE || snapshot.status == NetworkStatus.CAPTIVE_PORTAL) {
            return PolicyDecision.WaitingForNetwork("NETWORK_UNAVAILABLE")
        }
        if (snapshot.isRoaming && !snapshot.allowRoaming) {
            return PolicyDecision.WaitingForRoaming("ROAMING_DISABLED")
        }
        val isBackup = transfer.operationType.equals("BACKUP", ignoreCase = true) ||
            transfer.direction.equals("BACKUP", ignoreCase = true)
        if (isBackup && snapshot.requireChargingForBackup && !snapshot.isCharging) {
            return PolicyDecision.WaitingForCharging("CHARGING_REQUIRED")
        }
        if (isBackup && snapshot.batterySaverActive) {
            return PolicyDecision.WaitingForBattery("BATTERY_SAVER_ACTIVE")
        }
        if (isBackup && !snapshot.batteryNotLow) {
            return PolicyDecision.WaitingForBattery("BATTERY_LOW")
        }
        if (!isBackup && snapshot.status == NetworkStatus.VALIDATED_METERED && !snapshot.allowMeteredTransfers) {
            return PolicyDecision.WaitingForNetwork("METERED_NETWORK_DISABLED")
        }
        if (isBackup && snapshot.status == NetworkStatus.VALIDATED_METERED && snapshot.requireWifiForBackup) {
            return PolicyDecision.WaitingForNetwork("BACKUP_WIFI_ONLY")
        }
        return PolicyDecision.Allowed
    }
}

class ConnectivityNetworkMonitor(private val context: Context) : NetworkMonitor {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val _status = kotlinx.coroutines.flow.MutableStateFlow(readStatus())
    override val status: Flow<NetworkStatus> = _status

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) = publish()
        override fun onCapabilitiesChanged(network: android.net.Network, capabilities: NetworkCapabilities) = publish()
        override fun onLost(network: android.net.Network) = publish()
    }

    fun start() {
        runCatching { connectivity.registerDefaultNetworkCallback(callback) }
        publish()
    }

    fun stop() = runCatching { connectivity.unregisterNetworkCallback(callback) }

    override fun current(): NetworkStatus = readStatus()

    override fun snapshot(preferences: UserPreferencesState): NetworkPolicySnapshot {
        val capabilities = runCatching {
            connectivity?.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        }.getOrNull()
        val power = context.getSystemService(PowerManager::class.java)
        val battery = context.getSystemService(BatteryManager::class.java)
        val batteryLevel = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        return NetworkPolicySnapshot(
            status = readStatus(capabilities),
            allowMeteredTransfers = preferences.allowMeteredTransfers,
            allowRoaming = preferences.allowRoaming,
            requireChargingForBackup = preferences.backupChargingOnly,
            batterySaverActive = power?.isPowerSaveMode == true,
            isRoaming = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) == false,
            isCharging = battery?.isCharging == true,
            batteryNotLow = batteryLevel < 0 || batteryLevel >= 15,
            requireWifiForBackup = preferences.backupWifiOnly
        )
    }

    private fun publish() { _status.value = readStatus() }

    private fun readStatus(): NetworkStatus = readStatus(
        runCatching {
            connectivity?.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        }.getOrNull()
    )

    private fun readStatus(capabilities: NetworkCapabilities?): NetworkStatus {
        if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkStatus.UNAVAILABLE
        }
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) {
            return NetworkStatus.CAPTIVE_PORTAL
        }
        return if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
            NetworkStatus.VALIDATED_UNMETERED
        } else {
            NetworkStatus.VALIDATED_METERED
        }
    }
}

class TransferNetworkGate(
    private val monitor: NetworkMonitor,
    private val policy: NetworkPolicy,
    private val preferences: suspend () -> UserPreferencesState
) {
    suspend fun check(transfer: LocalTransfer): PolicyDecision =
        policy.canStart(transfer, monitor.snapshot(preferences()))
}
