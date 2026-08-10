package com.tenkultra.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

@HiltAndroidApp
class IptvApp : Application(), ImageLoaderFactory {

    /**
     * Capture the stack trace of any fatal crash to a file BEFORE the process dies, so a
     * crash can be diagnosed on a box with no adb/logcat. Retrieve it from a file manager at:
     *   Android/data/com.tenkultra.tv/files/last_crash.txt
     * The previous (system) handler is still invoked, so the normal crash behaviour is unchanged.
     */
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val dir = getExternalFilesDir(null) ?: filesDir
                File(dir, "last_crash.txt").writeText(
                    "Thread: ${thread.name}\nWhen: ${System.currentTimeMillis()}\n\n$sw"
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ImageLoaderEntryPoint {
        fun okHttpClient(): OkHttpClient
    }

    /** Route Coil through the authenticated OkHttp client so portal screenshots load. */
    override fun newImageLoader(): ImageLoader {
        val okHttpClient = EntryPointAccessors
            .fromApplication(this, ImageLoaderEntryPoint::class.java)
            .okHttpClient()
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .crossfade(180) // smooth premium fade-in for posters (cached ones still feel instant)
            .respectCacheHeaders(false) // posters don't change — cache aggressively
            .memoryCache {
                MemoryCache.Builder(this)
                    // 15%, not 30%: the dual-player live engine needs the heap headroom on
                    // low-RAM boxes (30% + two stream buffers = OOM). Posters still have the
                    // 150MB disk cache behind this, so scrolling stays smooth.
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150 MB poster cache
                    .build()
            }
            .build()
    }
}
