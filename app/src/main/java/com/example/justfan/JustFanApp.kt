package com.example.justfan

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.justfan.data.notification.OneSignalManager
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class JustFanApp : Application(), ImageLoaderFactory {

    companion object {
        const val ONESIGNAL_APP_ID = "1fbba4bb-4446-4bae-829d-36a74cd95adb"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize OneSignal SDK v5, notification channels, and listeners once via OneSignalManager
        OneSignalManager.initialize(this)
    }

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
                    )
                    .header("Referer", "https://imgchest.com/")
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }
}
