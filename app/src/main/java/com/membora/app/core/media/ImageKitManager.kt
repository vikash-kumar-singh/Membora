package com.membora.app.core.media

import android.content.Context
import com.imagekit.android.ImageKit
import com.imagekit.android.entity.TransformationPosition
import com.membora.app.BuildConfig

/** Initializes the ImageKit Android SDK once for the entire application process. */
object ImageKitManager {
    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            require(BuildConfig.IMAGEKIT_PUBLIC_KEY.isNotBlank()) {
                "IMAGEKIT_PUBLIC_KEY is not configured"
            }
            require(BuildConfig.IMAGEKIT_URL_ENDPOINT.startsWith("https://")) {
                "IMAGEKIT_URL_ENDPOINT must use HTTPS"
            }
            ImageKit.init(
                context = context.applicationContext,
                publicKey = BuildConfig.IMAGEKIT_PUBLIC_KEY,
                urlEndpoint = BuildConfig.IMAGEKIT_URL_ENDPOINT,
                transformationPosition = TransformationPosition.PATH
            )
            initialized = true
        }
    }
}
