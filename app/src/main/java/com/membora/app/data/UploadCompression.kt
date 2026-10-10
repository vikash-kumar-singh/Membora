package com.membora.app.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.roundToInt

data class PreparedUpload(
    val bytes: ByteArray,
    val contentType: String,
    val fileName: String,
    val originalSizeBytes: Int
) {
    val savedPercent: Int
        get() = if (originalSizeBytes <= 0) 0 else
            ((originalSizeBytes - bytes.size) * 100.0 / originalSizeBytes).roundToInt().coerceAtLeast(0)
}

/** Prepares bounded payloads before anything is sent to the configured media service. */
object UploadCompression {
    private const val MAX_INPUT_BYTES = 25 * 1024 * 1024
    const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024
    private const val JPEG_QUALITY = 82
    private const val PROFILE_MAX_EDGE = 1_600
    private const val DOCUMENT_IMAGE_MAX_EDGE = 2_200
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    fun preparePhoto(contentResolver: ContentResolver, uri: Uri): Result<PreparedUpload> = runCatching {
        val original = readBytes(contentResolver, uri)
        encodeJpeg(
            original = original,
            fileName = displayName(contentResolver, uri, "profile-photo"),
            maximumEdge = PROFILE_MAX_EDGE
        )
    }

    fun prepareDocument(contentResolver: ContentResolver, uri: Uri): Result<PreparedUpload> = runCatching {
        val contentType = contentResolver.getType(uri)?.lowercase(Locale.ROOT).orEmpty()
        val original = readBytes(contentResolver, uri)
        val name = displayName(contentResolver, uri, "document")
        require(original.isNotEmpty()) { "The selected document is empty" }
        when {
            contentType == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) -> {
                // PDFs usually contain already-compressed streams. Gzipping the entire file
                // makes it unreadable when served directly by ImageKit, so preserve valid bytes.
                require(original.size <= MAX_UPLOAD_BYTES) { "The PDF is larger than 10 MB" }
                PreparedUpload(
                    bytes = original,
                    contentType = "application/pdf",
                    fileName = name.withExtension("pdf"),
                    originalSizeBytes = original.size
                )
            }

            contentType.startsWith("image/") ||
                IMAGE_EXTENSIONS.any { name.endsWith(".$it", ignoreCase = true) } -> encodeJpeg(
                original = original,
                fileName = name,
                maximumEdge = DOCUMENT_IMAGE_MAX_EDGE
            )

            contentType == "application/msword" || name.endsWith(".doc", ignoreCase = true) ->
                preserveDocument(original, name, "application/msword", "doc")

            contentType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                name.endsWith(".docx", ignoreCase = true) -> preserveDocument(
                original,
                name,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx"
            )

            else -> error("Choose a PDF, DOC, DOCX, JPG, JPEG, PNG, or WEBP document")
        }
    }

    private fun preserveDocument(
        original: ByteArray,
        fileName: String,
        contentType: String,
        extension: String
    ): PreparedUpload {
        require(original.size <= MAX_UPLOAD_BYTES) { "The document is larger than 10 MB" }
        return PreparedUpload(
            bytes = original,
            contentType = contentType,
            fileName = fileName.withExtension(extension),
            originalSizeBytes = original.size
        )
    }

    private fun encodeJpeg(
        original: ByteArray,
        fileName: String,
        maximumEdge: Int
    ): PreparedUpload {
        val decoded = decodeBitmap(original, maximumEdge)
        val resized = resizeBitmap(decoded, maximumEdge)
        val opaque = createBitmap(resized.width, resized.height, Bitmap.Config.ARGB_8888)
        Canvas(opaque).apply {
            drawColor(Color.WHITE)
            drawBitmap(resized, 0f, 0f, null)
        }
        val output = ByteArrayOutputStream()
        check(opaque.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
            "Unable to compress this image"
        }
        if (opaque !== resized) opaque.recycle()
        if (resized !== decoded) resized.recycle()
        decoded.recycle()
        val bytes = output.toByteArray()
        require(bytes.size <= MAX_UPLOAD_BYTES) { "The compressed image is larger than 10 MB" }
        return PreparedUpload(
            bytes = bytes,
            contentType = "image/jpeg",
            fileName = fileName.withExtension("jpg"),
            originalSizeBytes = original.size
        )
    }

    private fun decodeBitmap(bytes: ByteArray, maximumEdge: Int): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val (width, height) = targetDimensions(info.size.width, info.size.height, maximumEdge)
                decoder.setTargetSize(width, height)
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unable to read this image" }
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maximumEdge * 2 ||
            bounds.outHeight / sampleSize > maximumEdge * 2
        ) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: error("Unable to read this image")
    }

    private fun resizeBitmap(bitmap: Bitmap, maximumEdge: Int): Bitmap {
        val (width, height) = targetDimensions(bitmap.width, bitmap.height, maximumEdge)
        return if (width == bitmap.width && height == bitmap.height) bitmap
        else bitmap.scale(width, height, true)
    }

    private fun targetDimensions(width: Int, height: Int, maximumEdge: Int): Pair<Int, Int> {
        require(width > 0 && height > 0) { "Unable to read this image" }
        val longestEdge = maxOf(width, height)
        if (longestEdge <= maximumEdge) return width to height
        val scale = maximumEdge.toDouble() / longestEdge
        return (width * scale).roundToInt().coerceAtLeast(1) to
            (height * scale).roundToInt().coerceAtLeast(1)
    }

    private fun readBytes(contentResolver: ContentResolver, uri: Uri): ByteArray {
        return contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_INPUT_BYTES) {
                    "Choose a file smaller than 25 MB"
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("Unable to open this file")
    }

    private fun displayName(contentResolver: ContentResolver, uri: Uri, fallback: String): String {
        val name = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
        return name?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: fallback
    }

    private fun String.withExtension(extension: String): String {
        val stem = substringBeforeLast('.', this).ifBlank { "document" }
        return "$stem.$extension"
    }

}
