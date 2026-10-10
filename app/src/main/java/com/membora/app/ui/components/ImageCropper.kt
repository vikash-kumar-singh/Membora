package com.membora.app.ui.components

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.yalantis.ucrop.UCrop
import java.io.File
import java.util.UUID

enum class ImageCropShape {
    CIRCLE,
    ROUNDED_SQUARE
}

/**
 * Returns a launcher that opens a locked 1:1 crop editor and only returns its output.
 * The original camera/gallery image is never passed to an uploader.
 */
@Composable
fun rememberSquareImageCropper(
    title: String,
    shape: ImageCropShape,
    onCropped: (Uri) -> Unit,
    onError: (String) -> Unit
): (Uri) -> Unit {
    val context = LocalContext.current
    val currentOnCropped = rememberUpdatedState(onCropped)
    val currentOnError = rememberUpdatedState(onError)
    val cropResultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val croppedUri = result.data?.let(UCrop::getOutput)
                if (croppedUri != null) currentOnCropped.value(croppedUri)
                else currentOnError.value("The cropped image could not be read")
            }
            UCrop.RESULT_ERROR -> {
                val error = result.data?.let(UCrop::getError)
                currentOnError.value(error?.localizedMessage ?: "Unable to crop that image")
            }
        }
    }

    return remember(context, title, shape, cropResultLauncher) {
        { sourceUri: Uri ->
            runCatching {
                val outputDirectory = File(context.cacheDir, "cropped-images").apply { mkdirs() }
                val outputUri = Uri.fromFile(File(outputDirectory, "crop-${UUID.randomUUID()}.jpg"))
                val options = UCrop.Options().apply {
                    setToolbarTitle(title)
                    setCompressionFormat(Bitmap.CompressFormat.JPEG)
                    setCompressionQuality(92)
                    setFreeStyleCropEnabled(false)
                    setHideBottomControls(false)
                    setShowCropFrame(true)
                    setShowCropGrid(true)
                    setCircleDimmedLayer(shape == ImageCropShape.CIRCLE)
                }
                UCrop.of(sourceUri, outputUri)
                    .withAspectRatio(1f, 1f)
                    .withMaxResultSize(1600, 1600)
                    .withOptions(options)
                    .getIntent(context)
            }.fold(
                onSuccess = cropResultLauncher::launch,
                onFailure = { currentOnError.value(it.localizedMessage ?: "Unable to open image cropper") }
            )
        }
    }
}
