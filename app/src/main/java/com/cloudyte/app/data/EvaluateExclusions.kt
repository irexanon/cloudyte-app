package com.cloudyte.app.data

import com.cloudyte.app.db.ExclusionRuleType
import com.cloudyte.app.db.LocalExclusion
import java.util.Locale

data class ExclusionCandidate(
    val fileName: String,
    val relativePath: String,
    val fileUri: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val isDirectory: Boolean = false
)

enum class ExclusionSkipReason {
    NONE,
    GLOBAL_MAX_SIZE,
    EXCLUSION_RULE
}

data class ExclusionEvaluationResult(
    val excluded: Boolean,
    val reason: ExclusionSkipReason = ExclusionSkipReason.NONE,
    val matchedRule: LocalExclusion? = null,
    val humanReason: String? = null
)

enum class ExclusionSizeUnit(val label: String, val multiplierBytes: Long) {
    KB("KB (1,024 B)", 1024L),
    MB("MB (1,024 KB)", 1024L * 1024L),
    GB("GB (1,024 MB)", 1024L * 1024L * 1024L)
}

sealed interface ExclusionValidationResult {
    data class Valid(
        val normalizedPattern: String,
        val sizeLimitBytes: Long? = null
    ) : ExclusionValidationResult

    data class Invalid(val message: String) : ExclusionValidationResult
}

object EvaluateExclusions {

    fun validateRuleInput(
        ruleType: ExclusionRuleType,
        pattern: String,
        isRegex: Boolean,
        sizeValueText: String = "",
        sizeUnit: ExclusionSizeUnit = ExclusionSizeUnit.MB,
        sourceUri: String? = null
    ): ExclusionValidationResult {
        val trimmed = pattern.trim()
        return when (ruleType) {
            ExclusionRuleType.HIDDEN -> {
                ExclusionValidationResult.Valid(
                    normalizedPattern = trimmed.ifBlank { ".*" }
                )
            }
            ExclusionRuleType.FOLDER_PATH -> {
                if (trimmed.isBlank() && sourceUri.isNullOrBlank()) {
                    ExclusionValidationResult.Invalid("Select a folder or enter a folder path (for example: WhatsApp/Media).")
                } else {
                    val normalized = trimmed.replace("\\", "/").trim('/')
                    if (normalized.isBlank() && sourceUri.isNullOrBlank()) {
                        ExclusionValidationResult.Invalid("Folder path cannot consist only of slashes.")
                    } else {
                        ExclusionValidationResult.Valid(
                            normalizedPattern = normalized.ifBlank { sourceUri.orEmpty() }
                        )
                    }
                }
            }
            ExclusionRuleType.FILE_PATH -> {
                if (trimmed.isBlank() && sourceUri.isNullOrBlank()) {
                    ExclusionValidationResult.Invalid("Select a file or enter a file path (for example: Documents/secrets.kdbx).")
                } else {
                    val normalized = trimmed.replace("\\", "/").trimStart('/')
                    ExclusionValidationResult.Valid(
                        normalizedPattern = normalized.ifBlank { sourceUri.orEmpty() }
                    )
                }
            }
            ExclusionRuleType.EXTENSION -> {
                val tokens = trimmed.split(",", ";", " ")
                    .map { it.trim().removePrefix("*").removePrefix(".").lowercase(Locale.US) }
                    .filter { it.isNotBlank() }
                if (tokens.isEmpty()) {
                    return ExclusionValidationResult.Invalid("Enter one or more file extensions (for example: tmp, log, bak).")
                }
                val bad = tokens.firstOrNull { !it.matches(Regex("^[a-z0-9_+\\-]{1,20}$")) }
                if (bad != null) {
                    return ExclusionValidationResult.Invalid("Extension '$bad' contains invalid characters. Use letters or numbers such as tmp, bak, or mkv.")
                }
                ExclusionValidationResult.Valid(normalizedPattern = tokens.joinToString(", "))
            }
            ExclusionRuleType.MIME_TYPE -> {
                if (trimmed.isBlank()) {
                    return ExclusionValidationResult.Invalid("Enter a MIME type such as video/*, audio/mp4, or application/zip.")
                }
                val lower = trimmed.lowercase(Locale.US)
                val slashIdx = lower.indexOf('/')
                if (slashIdx <= 0 || slashIdx == lower.lastIndex || lower.contains(' ')) {
                    return ExclusionValidationResult.Invalid("Invalid MIME type '$trimmed'. Format must be type/subtype, e.g. video/* or application/pdf.")
                }
                ExclusionValidationResult.Valid(normalizedPattern = lower)
            }
            ExclusionRuleType.PATH_PATTERN -> {
                if (trimmed.isBlank()) {
                    return ExclusionValidationResult.Invalid("Enter a pattern such as *.tmp, cache/*, or a regular expression.")
                }
                if (isRegex) {
                    try {
                        Regex(trimmed, RegexOption.IGNORE_CASE)
                    } catch (e: Exception) {
                        return ExclusionValidationResult.Invalid("Invalid regular expression: ${e.message ?: "check syntax"}")
                    }
                }
                ExclusionValidationResult.Valid(normalizedPattern = trimmed)
            }
            ExclusionRuleType.MAX_SIZE_BYTES -> {
                val numeric = sizeValueText.trim().toDoubleOrNull()
                    ?: return ExclusionValidationResult.Invalid("Enter a positive number for the maximum size in ${sizeUnit.label}.")
                if (!numeric.isFinite() || numeric <= 0.0) {
                    return ExclusionValidationResult.Invalid("Size threshold must be greater than 0 ${sizeUnit.label}.")
                }
                val bytesDouble = numeric * sizeUnit.multiplierBytes.toDouble()
                if (bytesDouble > Long.MAX_VALUE.toDouble() / 2.0) {
                    return ExclusionValidationResult.Invalid("Size threshold is too large.")
                }
                val bytes = bytesDouble.toLong().coerceAtLeast(1L)
                val label = trimmed.ifBlank { "${sizeValueText.trim()} ${sizeUnit.name}" }
                ExclusionValidationResult.Valid(
                    normalizedPattern = label,
                    sizeLimitBytes = bytes
                )
            }
        }
    }

    fun isExcluded(filePathOrName: String, exclusions: List<LocalExclusion>): Boolean {
        val normalized = filePathOrName.replace("\\", "/")
        val fileName = normalized.substringAfterLast('/')
        return evaluate(
            candidate = ExclusionCandidate(
                fileName = fileName,
                relativePath = normalized.trimStart('/'),
                fileUri = null,
                mimeType = null,
                sizeBytes = 0L,
                isDirectory = false
            ),
            exclusions = exclusions,
            globalMaxFileSizeBytes = Long.MAX_VALUE
        ).excluded
    }

    fun evaluate(
        candidate: ExclusionCandidate,
        exclusions: List<LocalExclusion>,
        globalMaxFileSizeBytes: Long = Long.MAX_VALUE
    ): ExclusionEvaluationResult {
        if (!candidate.isDirectory && globalMaxFileSizeBytes in 1L until Long.MAX_VALUE && candidate.sizeBytes > globalMaxFileSizeBytes) {
            return ExclusionEvaluationResult(
                excluded = true,
                reason = ExclusionSkipReason.GLOBAL_MAX_SIZE,
                humanReason = "Exceeds max backup file size (${formatBytes(candidate.sizeBytes)} > ${formatBytes(globalMaxFileSizeBytes)})"
            )
        }

        val activeRules = exclusions.filter { it.enabled }
        if (activeRules.isEmpty()) return ExclusionEvaluationResult(excluded = false)

        val normalizedPath = candidate.relativePath.replace("\\", "/").trim('/')
        val fileName = candidate.fileName.ifBlank { normalizedPath.substringAfterLast('/') }

        for (rule in activeRules) {
            if (matchesRule(candidate, normalizedPath, fileName, rule)) {
                return ExclusionEvaluationResult(
                    excluded = true,
                    reason = ExclusionSkipReason.EXCLUSION_RULE,
                    matchedRule = rule,
                    humanReason = "Matched exclusion rule (${rule.effectiveRuleType().displayName}: ${rule.pattern})"
                )
            }
        }
        return ExclusionEvaluationResult(excluded = false)
    }

    private fun matchesRule(
        candidate: ExclusionCandidate,
        normalizedPath: String,
        fileName: String,
        rule: LocalExclusion
    ): Boolean {
        return when (rule.effectiveRuleType()) {
            ExclusionRuleType.HIDDEN -> {
                fileName.startsWith(".") ||
                    normalizedPath.split('/').any { it.startsWith(".") }
            }
            ExclusionRuleType.FOLDER_PATH -> {
                val sourceUri = rule.sourceUri?.trim().orEmpty()
                if (sourceUri.isNotBlank() && !candidate.fileUri.isNullOrBlank()) {
                    if (candidate.fileUri == sourceUri || candidate.fileUri.startsWith("$sourceUri/") ||
                        candidate.fileUri.startsWith("$sourceUri%2F", ignoreCase = true)
                    ) {
                        return true
                    }
                }
                val folderPattern = rule.pattern.replace("\\", "/").trim('/').lowercase(Locale.US)
                if (folderPattern.isBlank()) return false
                val candidatePathLower = normalizedPath.lowercase(Locale.US)
                if (candidate.isDirectory) {
                    candidatePathLower == folderPattern ||
                        candidatePathLower.startsWith("$folderPattern/") ||
                        fileName.lowercase(Locale.US) == folderPattern
                } else {
                    candidatePathLower.startsWith("$folderPattern/") ||
                        "/$candidatePathLower".contains("/$folderPattern/")
                }
            }
            ExclusionRuleType.FILE_PATH -> {
                if (candidate.isDirectory) return false
                val sourceUri = rule.sourceUri?.trim().orEmpty()
                if (sourceUri.isNotBlank() && candidate.fileUri == sourceUri) {
                    return true
                }
                val filePattern = rule.pattern.replace("\\", "/").trim('/').lowercase(Locale.US)
                if (filePattern.isBlank()) return false
                val candidatePathLower = normalizedPath.lowercase(Locale.US)
                candidatePathLower == filePattern || fileName.lowercase(Locale.US) == filePattern
            }
            ExclusionRuleType.EXTENSION -> {
                if (candidate.isDirectory) return false
                val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
                if (ext.isBlank()) return false
                val blocked = rule.pattern.split(",", ";", " ")
                    .map { it.trim().removePrefix("*").removePrefix(".").lowercase(Locale.US) }
                    .filter { it.isNotBlank() }
                ext in blocked
            }
            ExclusionRuleType.MIME_TYPE -> {
                if (candidate.isDirectory) return false
                val candidateMime = candidate.mimeType?.trim()?.lowercase(Locale.US) ?: return false
                val pattern = rule.pattern.trim().lowercase(Locale.US)
                if (pattern.endsWith("/*")) {
                    val prefix = pattern.removeSuffix("*")
                    candidateMime.startsWith(prefix)
                } else {
                    candidateMime == pattern
                }
            }
            ExclusionRuleType.MAX_SIZE_BYTES -> {
                if (candidate.isDirectory) return false
                val limit = rule.sizeLimitBytes ?: return false
                limit > 0L && candidate.sizeBytes > limit
            }
            ExclusionRuleType.PATH_PATTERN -> {
                val pattern = rule.pattern.trim()
                if (pattern.isEmpty()) return false
                if (rule.isRegex) {
                    try {
                        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                        regex.containsMatchIn(normalizedPath) || regex.containsMatchIn(fileName)
                    } catch (_: Exception) {
                        false
                    }
                } else {
                    val globRegex = globToRegex(pattern)
                    try {
                        val regex = Regex(globRegex, RegexOption.IGNORE_CASE)
                        regex.matches(fileName) ||
                            regex.matches(normalizedPath) ||
                            regex.containsMatchIn(normalizedPath)
                    } catch (_: Exception) {
                        normalizedPath.contains(pattern, ignoreCase = true) ||
                            fileName.contains(pattern, ignoreCase = true)
                    }
                }
            }
        }
    }

    private fun globToRegex(glob: String): String {
        val sb = StringBuilder("^")
        var i = 0
        val len = glob.length
        while (i < len) {
            val c = glob[i]
            when (c) {
                '*' -> sb.append(".*")
                '?' -> sb.append('.')
                '.' -> sb.append("\\.")
                '\\', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']' -> sb.append('\\').append(c)
                else -> sb.append(c)
            }
            i++
        }
        sb.append("$")
        return sb.toString()
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
            bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024.0)
            else -> "$bytes B"
        }
    }
}
