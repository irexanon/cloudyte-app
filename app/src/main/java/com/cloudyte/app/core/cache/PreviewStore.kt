package com.cloudyte.app.core.cache

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class PreviewStore(context: Context) {
    private val root = File(context.applicationContext.cacheDir, "cloudyte_previews").apply { mkdirs() }
    private val accessTimestamps = ConcurrentHashMap<String, Long>()

    suspend fun get(key: String): File? = withContext(Dispatchers.IO) {
        val safe = safeName(key)
        File(root, safe).takeIf { it.isFile }?.also { file ->
            val now = System.currentTimeMillis()
            accessTimestamps[file.name] = now
            file.setLastModified(now)
        }
    }

    fun createTempFile(key: String): File {
        val safe = safeName(key)
        return File(root, ".${safe}.${System.nanoTime()}.tmp")
    }

    suspend fun commitTempFile(key: String, temp: File): File = withContext(Dispatchers.IO) {
        val safe = safeName(key)
        val target = File(root, safe)
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        check(target.exists() && target.length() > 0L) { "Unable to commit preview cache entry" }
        val now = System.currentTimeMillis()
        accessTimestamps[target.name] = now
        target.setLastModified(now)
        target
    }

    suspend fun put(key: String, bytes: ByteArray): File = withContext(Dispatchers.IO) {
        val safe = safeName(key)
        val target = File(root, safe)
        val temp = File(root, ".${target.name}.tmp")
        temp.outputStream().use { it.write(bytes) }
        if (target.exists()) target.delete()
        check(temp.renameTo(target)) { "Unable to commit preview cache entry" }
        val now = System.currentTimeMillis()
        accessTimestamps[target.name] = now
        target.setLastModified(now)
        target
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        accessTimestamps.clear()
        root.listFiles()?.forEach { it.delete() }
    }

    suspend fun enforceQuota(maxBytes: Long = DEFAULT_QUOTA_BYTES) = withContext(Dispatchers.IO) {
        val files = root.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return@withContext
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return@withContext
        files.sortedBy { file ->
            maxOf(file.lastModified(), accessTimestamps[file.name] ?: 0L)
        }.forEach { file ->
            if (total <= maxBytes) return@forEach
            total -= file.length()
            accessTimestamps.remove(file.name)
            file.delete()
        }
    }

    private fun safeName(key: String): String = key.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        const val DEFAULT_QUOTA_BYTES = 512L * 1024L * 1024L
    }
}

