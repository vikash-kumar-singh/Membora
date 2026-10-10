package com.membora.app.core.media

import android.net.Uri

data class MediaUploadResult(
    val fileId: String,
    val name: String,
    val url: Uri,
    val mediaType: MediaType,
    val storedSizeBytes: Long,
    val privateFile: Boolean
)
