package com.cloudyte.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ExclusionRuleType(val displayName: String) {
    FOLDER_PATH("Folder Path"),
    FILE_PATH("Individual File Path"),
    HIDDEN("Hidden Files & Folders"),
    EXTENSION("File Extension"),
    MIME_TYPE("MIME Type"),
    PATH_PATTERN("Path Pattern / Regex"),
    MAX_SIZE_BYTES("Maximum File Size")
}

@Entity(tableName = "exclusions")
data class LocalExclusion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pattern: String,
    val isRegex: Boolean = false,
    val enabled: Boolean = true,
    val ruleType: String = ExclusionRuleType.PATH_PATTERN.name,
    val sizeLimitBytes: Long? = null,
    val sourceUri: String? = null
) {
    fun effectiveRuleType(): ExclusionRuleType =
        runCatching { ExclusionRuleType.valueOf(ruleType) }.getOrDefault(ExclusionRuleType.PATH_PATTERN)
}
