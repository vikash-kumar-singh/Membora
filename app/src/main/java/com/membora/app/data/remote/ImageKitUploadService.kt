package com.membora.app.data.remote

import android.net.Uri
import com.membora.app.core.media.MediaType
import com.membora.app.core.media.MediaUploadResult
import com.membora.app.firebase.FirebaseServices
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Uploads only after the Membora backend has authenticated the Firebase user and issued a
 * short-lived ImageKit signature. The ImageKit private key must never be shipped in this app.
 */
object ImageKitUploadService {
    private val uploadApi: ImageKitUploadApi by lazy {
        val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://upload.imagekit.io/")
            .client(http)
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(ImageKitUploadApi::class.java)
    }

    fun uploadCurrentUserFile(
        path: String,
        bytes: ByteArray,
        contentType: String?,
        mediaType: MediaType,
        onResult: (Result<MediaUploadResult>) -> Unit
    ) {
        if (FirebaseServices.auth?.currentUser == null) {
            onResult(Result.failure(IllegalStateException("Sign in before uploading files")))
            return
        }
        val safePath = sanitizePath(path)
            ?: return onResult(Result.failure(IllegalArgumentException("The upload path is invalid")))
        if (bytes.isEmpty()) {
            onResult(Result.failure(IllegalArgumentException("The selected file is empty")))
            return
        }

        // Firebase Storage supports every upload locally without exposing a server secret.
        // Private documents are persisted as gs:// references and downloaded through the
        // authenticated SDK; public images retain download URLs for direct rendering.
        FirebaseServices.uploadCurrentUserFile(
            path = safePath,
            bytes = bytes,
            contentType = contentType,
            privateFile = mediaType.privateFile
        ) { result ->
            onResult(result.map { uri ->
                MediaUploadResult(
                    fileId = safePath,
                    name = safePath.substringAfterLast('/'),
                    url = uri,
                    mediaType = mediaType,
                    storedSizeBytes = bytes.size.toLong(),
                    privateFile = mediaType.privateFile
                )
            })
        }
    }

    fun resolvePrivateUrl(url: String, onResult: (Result<Uri>) -> Unit) {
        if (url.startsWith("gs://")) {
            FirebaseServices.downloadCurrentUserFile(url, onResult)
            return
        }
        val user = FirebaseServices.auth?.currentUser
            ?: return onResult(Result.failure(IllegalStateException("Sign in before opening documents")))
        user.getIdToken(false).addOnCompleteListener { tokenTask ->
            val idToken = tokenTask.result?.token
            if (!tokenTask.isSuccessful || idToken.isNullOrBlank()) {
                onResult(Result.failure(tokenTask.exception ?: IllegalStateException("Unable to authenticate document access")))
                return@addOnCompleteListener
            }
            ApiClient.api.imageKitSignedUrl(idToken, url).enqueue(object : Callback<ImageKitSignedUrlResponse> {
                override fun onResponse(
                    call: Call<ImageKitSignedUrlResponse>,
                    response: Response<ImageKitSignedUrlResponse>
                ) {
                    val signed = response.body()?.url?.takeIf(String::isNotBlank)?.let(Uri::parse)
                    if (!response.isSuccessful || signed?.scheme != "https") {
                        onResult(Result.failure(IllegalStateException("Unable to authorize document access.")))
                    } else {
                        onResult(Result.success(signed))
                    }
                }

                override fun onFailure(call: Call<ImageKitSignedUrlResponse>, throwable: Throwable) {
                    FirebaseServices.recordException(throwable)
                    onResult(Result.failure(IllegalStateException("Cannot reach the document service.", throwable)))
                }
            })
        }
    }

    private fun requestUploadAuthentication(
        idToken: String,
        safePath: String,
        bytes: ByteArray,
        contentType: String?,
        mediaType: MediaType,
        onResult: (Result<MediaUploadResult>) -> Unit
    ) {
        ApiClient.api.imageKitAuth(idToken).enqueue(object : Callback<ImageKitAuthResponse> {
            override fun onResponse(call: Call<ImageKitAuthResponse>, response: Response<ImageKitAuthResponse>) {
                val auth = response.body()
                if (!response.isSuccessful || auth == null || !auth.isValid()) {
                    onResult(Result.failure(IllegalStateException(
                        if (response.code() == 401) "Your Firebase session expired. Sign in again."
                        else "ImageKit upload service is not configured on the Membora server (HTTP ${response.code()})."
                    )))
                    return
                }
                upload(auth, safePath, bytes, contentType, mediaType, onResult)
            }

            override fun onFailure(call: Call<ImageKitAuthResponse>, throwable: Throwable) {
                FirebaseServices.recordException(throwable)
                onResult(Result.failure(IllegalStateException(
                    "Cannot reach the Membora upload service. Check the server URL and connection.",
                    throwable
                )))
            }
        })
    }

    private fun upload(
        auth: ImageKitAuthResponse,
        safePath: String,
        bytes: ByteArray,
        contentType: String?,
        mediaType: MediaType,
        onResult: (Result<MediaUploadResult>) -> Unit
    ) {
        val fileName = safePath.substringAfterLast('/')
        val relativeFolder = safePath.substringBeforeLast('/', "")
        val folder = buildString {
            append(auth.folder.trimEnd('/'))
            if (relativeFolder.isNotBlank()) append('/').append(relativeFolder)
        }
        val body = bytes.toRequestBody(contentType?.toMediaTypeOrNull())
        val file = MultipartBody.Part.createFormData("file", fileName, body)
        uploadApi.upload(
            file = file,
            fileName = fileName.textBody(),
            publicKey = auth.publicKey.textBody(),
            signature = auth.signature.textBody(),
            expire = auth.expire.toString().textBody(),
            token = auth.token.textBody(),
            folder = folder.textBody(),
            useUniqueFileName = "false".textBody(),
            isPrivateFile = mediaType.privateFile.toString().textBody()
        ).enqueue(object : Callback<ImageKitUploadResponse> {
            override fun onResponse(call: Call<ImageKitUploadResponse>, response: Response<ImageKitUploadResponse>) {
                val uploaded = response.body()
                val uri = uploaded?.url?.takeIf(String::isNotBlank)?.let(Uri::parse)
                val expectedHost = Uri.parse(auth.urlEndpoint).host
                if (!response.isSuccessful || uri == null || uri.scheme != "https" ||
                    expectedHost.isNullOrBlank() || uri.host != expectedHost
                ) {
                    val error = IllegalStateException("ImageKit rejected the upload (HTTP ${response.code()}).")
                    FirebaseServices.recordException(error)
                    onResult(Result.failure(error))
                    return
                }
                FirebaseServices.logFileUploaded(contentType, bytes.size.toLong(), "imagekit")
                onResult(Result.success(MediaUploadResult(
                    fileId = uploaded.fileId,
                    name = uploaded.name.ifBlank { fileName },
                    url = uri,
                    mediaType = mediaType,
                    storedSizeBytes = uploaded.size.takeIf { it > 0 } ?: bytes.size.toLong(),
                    privateFile = mediaType.privateFile
                )))
            }

            override fun onFailure(call: Call<ImageKitUploadResponse>, throwable: Throwable) {
                FirebaseServices.recordException(throwable)
                onResult(Result.failure(IllegalStateException("ImageKit upload failed. Please retry.", throwable)))
            }
        })
    }

    private fun sanitizePath(path: String): String? {
        val segments = path.trim('/').split('/').filter(String::isNotBlank)
        if (segments.isEmpty() || segments.any { it == "." || it == ".." || !it.matches(Regex("[A-Za-z0-9._-]{1,128}")) }) {
            return null
        }
        return segments.joinToString("/")
    }

    private fun ImageKitAuthResponse.isValid(): Boolean = token.isNotBlank() && expire > 0 &&
        signature.matches(Regex("[a-fA-F0-9]{40}")) && publicKey.isNotBlank() &&
        folder.startsWith("/membora/users/") && urlEndpoint.startsWith("https://")

    private fun String.textBody(): RequestBody = toRequestBody("text/plain".toMediaTypeOrNull())
}

private interface ImageKitUploadApi {
    @Multipart
    @POST("api/v1/files/upload")
    fun upload(
        @Part file: MultipartBody.Part,
        @Part("fileName") fileName: RequestBody,
        @Part("publicKey") publicKey: RequestBody,
        @Part("signature") signature: RequestBody,
        @Part("expire") expire: RequestBody,
        @Part("token") token: RequestBody,
        @Part("folder") folder: RequestBody,
        @Part("useUniqueFileName") useUniqueFileName: RequestBody,
        @Part("isPrivateFile") isPrivateFile: RequestBody
    ): Call<ImageKitUploadResponse>
}
