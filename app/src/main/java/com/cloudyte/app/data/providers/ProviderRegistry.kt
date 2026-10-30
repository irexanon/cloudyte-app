package com.cloudyte.app.data.providers

import com.cloudyte.app.api.CloudyteApi

/** Resolves a stable provider adapter from the account type returned by the server. */
class ProviderRegistry(api: CloudyteApi) {
    private val providers = listOf(
        TelegramProvider(api),
        GoogleDriveProvider(api),
        OneDriveProvider(api),
        CloudyteProvider(api)
    ).associateBy { it.type }

    fun forAccountType(accountType: String?): CloudProvider {
        val normalized = accountType.orEmpty().lowercase()
        return when {
            normalized.contains("google") || normalized.contains("gdrive") -> providers.getValue("google_drive")
            normalized.contains("one") -> providers.getValue("onedrive")
            normalized.contains("tg") || normalized.contains("telegram") -> providers.getValue("telegram")
            else -> providers.getValue("cloudyte")
        }
    }
}
