package com.cloudyte.app.features.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.cloudyte.app.api.ApiClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

    fun createLocalFileMediaSourceFactory(context: Context): MediaSource.Factory {
        val localFactory = DefaultDataSource.Factory(context)
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        return DefaultMediaSourceFactory(localFactory, extractorsFactory)
    }

    fun createMediaSourceFactory(context: Context): MediaSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(false)

        val scopedAuthHttpFactory = DataSource.Factory {
            val delegate: HttpDataSource = httpFactory.createDataSource()
            object : DataSource by delegate {
                override fun open(dataSpec: DataSpec): Long {
                    val uri: Uri = dataSpec.uri
                    val baseUrl = ApiClient.API_BASE_URL
                    val apiHost = baseUrl.toHttpUrlOrNull()?.host.orEmpty()
                    val allowHttp = baseUrl.startsWith("http://", ignoreCase = true)
                    val schemeAllowed = uri.scheme.equals("https", ignoreCase = true) ||
                        (allowHttp && uri.scheme.equals("http", ignoreCase = true))
                    val isTrustedOrigin = schemeAllowed &&
                        !apiHost.isBlank() &&
                        uri.host.equals(apiHost, ignoreCase = true)
                    val token = ApiClient.getToken()
                    if (isTrustedOrigin && !token.isNullOrBlank()) {
                        delegate.setRequestProperty("Authorization", "Bearer $token")
                    } else {
                        delegate.clearRequestProperty("Authorization")
                    }
                    return delegate.open(dataSpec)
                }
            }
        }

        val upstreamFactory: DataSource.Factory = DefaultDataSource.Factory(context, scopedAuthHttpFactory)

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val schemeRoutingFactory = DataSource.Factory {
            val cachedDataSource = cacheDataSourceFactory.createDataSource()
            val directLocalDataSource = upstreamFactory.createDataSource()
            object : DataSource {
                private var activeDataSource: DataSource? = null

                override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
                    cachedDataSource.addTransferListener(transferListener)
                    directLocalDataSource.addTransferListener(transferListener)
                }

                override fun open(dataSpec: DataSpec): Long {
                    val scheme = dataSpec.uri.scheme?.lowercase().orEmpty()
                    val selected = if (scheme == "http" || scheme == "https") {
                        cachedDataSource
                    } else {
                        directLocalDataSource
                    }
                    activeDataSource = selected
                    return selected.open(dataSpec)
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    return activeDataSource?.read(buffer, offset, length)
                        ?: throw java.io.IOException("DataSource not opened")
                }

                override fun getUri(): Uri? = activeDataSource?.uri

                override fun getResponseHeaders(): Map<String, List<String>> =
                    activeDataSource?.responseHeaders ?: emptyMap()

                override fun close() {
                    try {
                        activeDataSource?.close()
                    } finally {
                        activeDataSource = null
                    }
                }
            }
        }

        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        return DefaultMediaSourceFactory(schemeRoutingFactory, extractorsFactory)
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
