package com.cloudyte.app.sync

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SyncLogLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

enum class SyncState {
    IDLE,
    SCANNING,
    SYNCING,
    SUCCESS,
    ERROR
}

data class SyncLog(
    val id: Long = System.currentTimeMillis(),
    val timeFormatted: String,
    val message: String,
    val level: SyncLogLevel = SyncLogLevel.INFO
)

data class SyncStatusInfo(
    val state: SyncState = SyncState.IDLE,
    val lastSyncTime: Long = 0L,
    val lastSyncSummary: String = "No backup performed yet",
    val currentAction: String = "",
    val currentFile: String = "",
    val uploadedFilesCount: Int = 0,
    val totalFilesCount: Int = 0
)

object SyncLogger {
    private const val PREFS_NAME = "sync_logger_prefs"
    private const val KEY_LOGS = "cached_logs"
    private const val MAX_LOGS = 300
    private const val MAX_FILE_BYTES = 256L * 1024L

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val _logsFlow = MutableStateFlow<List<SyncLog>>(emptyList())
    val logsFlow: StateFlow<List<SyncLog>> = _logsFlow.asStateFlow()

    private val _statusFlow = MutableStateFlow(SyncStatusInfo())
    val statusFlow: StateFlow<SyncStatusInfo> = _statusFlow.asStateFlow()

    private var prefs: SharedPreferences? = null
    private var logFile: File? = null
    private var rotatedLogFile: File? = null

    private val bearerRegex = Regex("""(?i)\bBearer\s+[A-Za-z0-9._\-+/=]+""")
    private val authHeaderRegex = Regex("""(?i)(Authorization\s*[:=]\s*)([^\s,;]+)""")
    private val querySecretRegex = Regex("""(?i)([?&](?:token|access_token|refresh_token|password|passphrase|secret|api_key)=)([^&\s]+)""")
    private val keyValSecretRegex = Regex("""(?i)\b(token|password|passphrase|secret|apiKey)\s*[:=]\s*("[^"]+"|'[^']+'|[^\s,;]+)""")

    fun redact(raw: String): String {
        if (raw.isEmpty()) return raw
        var out = bearerRegex.replace(raw, "Bearer [REDACTED]")
        out = authHeaderRegex.replace(out) { "${it.groupValues[1]}[REDACTED]" }
        out = querySecretRegex.replace(out) { "${it.groupValues[1]}[REDACTED]" }
        out = keyValSecretRegex.replace(out) { "${it.groupValues[1]}=[REDACTED]" }
        return out
    }

    @Synchronized
    fun init(context: Context) {
        val appContext = context.applicationContext
        if (prefs == null) {
            prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadLogsFromPrefs()
        }
        if (logFile == null) {
            val dir = File(appContext.filesDir, "logs").apply { mkdirs() }
            logFile = File(dir, "sync.log")
            rotatedLogFile = File(dir, "sync.log.1")
        }
    }

    private fun loadLogsFromPrefs() {
        val rawJson = prefs?.getString(KEY_LOGS, null) ?: return
        try {
            val jsonArray = JSONArray(rawJson)
            val list = mutableListOf<SyncLog>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    SyncLog(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        timeFormatted = obj.optString("time", ""),
                        message = redact(obj.optString("message", "")),
                        level = try {
                            SyncLogLevel.valueOf(obj.optString("level", "INFO"))
                        } catch (_: Exception) {
                            SyncLogLevel.INFO
                        }
                    )
                )
            }
            _logsFlow.value = list
        } catch (_: Exception) {
        }
    }

    private fun saveLogsToPrefs(list: List<SyncLog>) {
        try {
            val jsonArray = JSONArray()
            val takeList = list.takeLast(120)
            for (item in takeList) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("time", item.timeFormatted)
                obj.put("message", item.message)
                obj.put("level", item.level.name)
                jsonArray.put(obj)
            }
            prefs?.edit()?.putString(KEY_LOGS, jsonArray.toString())?.apply()
        } catch (_: Exception) {
        }
    }

    private fun appendToRotatingFile(level: SyncLogLevel, sanitizedMessage: String) {
        val active = logFile ?: return
        val backup = rotatedLogFile ?: return
        try {
            if (active.exists() && active.length() >= MAX_FILE_BYTES) {
                if (backup.exists()) backup.delete()
                active.renameTo(backup)
            }
            val line = "${dateTimeFormat.format(Date())} [${level.name}] $sanitizedMessage\n"
            active.appendText(line, Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun log(message: String, level: SyncLogLevel = SyncLogLevel.INFO) {
        val safeMessage = redact(message)
        val entry = SyncLog(
            id = System.currentTimeMillis() + (0..999).random(),
            timeFormatted = timeFormat.format(Date()),
            message = safeMessage,
            level = level
        )
        val current = _logsFlow.value.toMutableList()
        current.add(entry)
        while (current.size > MAX_LOGS) {
            current.removeAt(0)
        }
        _logsFlow.value = current
        saveLogsToPrefs(current)
        appendToRotatingFile(level, safeMessage)
    }

    fun updateStatus(
        state: SyncState,
        action: String = "",
        file: String = "",
        uploadedCount: Int = _statusFlow.value.uploadedFilesCount,
        totalCount: Int = _statusFlow.value.totalFilesCount,
        summary: String? = null
    ) {
        val prev = _statusFlow.value
        _statusFlow.value = prev.copy(
            state = state,
            currentAction = redact(action),
            currentFile = file,
            uploadedFilesCount = uploadedCount,
            totalFilesCount = totalCount,
            lastSyncTime = if (state == SyncState.SUCCESS || state == SyncState.ERROR) System.currentTimeMillis() else prev.lastSyncTime,
            lastSyncSummary = summary?.let(::redact) ?: prev.lastSyncSummary
        )
    }

    @Synchronized
    fun getExportText(context: Context? = null): String {
        context?.let { init(it) }
        val parts = mutableListOf<String>()
        rotatedLogFile?.takeIf { it.isFile && it.length() > 0L }?.let { file ->
            runCatching { file.readText(Charsets.UTF_8) }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
                parts += redact(it.trimEnd())
            }
        }
        logFile?.takeIf { it.isFile && it.length() > 0L }?.let { file ->
            runCatching { file.readText(Charsets.UTF_8) }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
                parts += redact(it.trimEnd())
            }
        }
        if (parts.isEmpty() && _logsFlow.value.isNotEmpty()) {
            parts += _logsFlow.value.joinToString("\n") { "${it.timeFormatted} [${it.level.name}] ${it.message}" }
        }
        return if (parts.isEmpty()) "No sync logs recorded yet." else parts.joinToString("\n")
    }

    fun exportToUri(context: Context, uri: Uri): Result<Int> {
        return runCatching {
            val content = getExportText(context)
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IllegalStateException("Could not open selected destination file for writing.")
            stream.use { out ->
                val bytes = content.toByteArray(Charsets.UTF_8)
                out.write(bytes)
                out.flush()
                bytes.size
            }
        }
    }

    @Synchronized
    fun clearLogs() {
        _logsFlow.value = emptyList()
        prefs?.edit()?.remove(KEY_LOGS)?.apply()
        runCatching { logFile?.delete() }
        runCatching { rotatedLogFile?.delete() }
    }
}
