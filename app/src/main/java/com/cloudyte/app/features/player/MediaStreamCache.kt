package com.cloudyte.app.features.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.cloudyte.app.api.ApiClient
import java.io.File

/**
 * Shared Media3 streaming cache & low-latency startup configuration for unencrypted
 * video/audio playback. Encrypted media always plays from decrypted local files via
 * [com.cloudyte.app.core.cache.PreviewFetcher] and bypasses [SimpleCache].
 */
@OptIn(UnstableApi::class)
object MediaStreamCache {
    const val CACHE_DIR_NAME = "media_stream_cache"
    private const val MAX_CACHE_BYTES = 150L * 1024L * 1024L

    @Volatile
    private var simpleCache: SimpleCache? = null
    private val cacheLock = Any()

    fun getCache(context: Context): SimpleCache {
        simpleCache?.let { return it }
        synchronized(cacheLock) {
            simpleCache?.let { return it }
            val appContext = context.applicationContext
            val cacheDir = File(appContext.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
            val dbProvider = StandaloneDatabaseProvider(appContext)
            return SimpleCache(
                cacheDir,
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                dbProvider
            ).also { simpleCache = it }
        }
    }

    fun clear(context: Context) {
        synchronized(cacheLock) {
            runCatching { simpleCache?.release() }
            simpleCache = null
            File(context.applicationContext.cacheDir, CACHE_DIR_NAME).deleteRecursively()
        }
    }

    fun createMediaSourceFactory(context: Context): MediaSource.Factory {
        val token = ApiClient.getToken()
        val headers = mutableMapOf<String, String>()
        if (!token.isNullOrEmpty()) {
            headers["Authorization"] = "Bearer $token"
        }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)

        val upstreamFactory: DataSource.Factory = DefaultDataSource.Factory(context, httpFactory)

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return DefaultMediaSourceFactory(cacheDataSourceFactory)
    }

    fun createFastLoadControl(): LoadControl {
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }
}
