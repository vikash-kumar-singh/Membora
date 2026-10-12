package com.membora.app.data.remote

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

data class AutomaticDeliveryRequest(
    val deliveryKey: String,
    val channel: String,
    val recipient: String,
    val templateKey: String,
    val parameters: List<String>
)

data class AutomaticDeliveryResponse(
    val status: String = "FAILED",
    val providerMessageId: String = "",
    val detail: String = "",
    val retryable: Boolean = false,
    val duplicate: Boolean = false
)

data class AutomaticDeliveryStatusResponse(
    val status: String = "UNKNOWN",
    val providerMessageId: String = "",
    val providerStatus: String = "",
    val detail: String = ""
)

interface MemboraApi {
    @GET("actuator/health")
    fun health(): Call<HealthResponse>

    @GET("api/v1/media/imagekit-auth")
    fun imageKitAuth(
        @Header("X-Firebase-ID-Token") firebaseIdToken: String
    ): Call<ImageKitAuthResponse>

    @GET("api/v1/media/signed-url")
    fun imageKitSignedUrl(
        @Header("X-Firebase-ID-Token") firebaseIdToken: String,
        @Query("url") url: String
    ): Call<ImageKitSignedUrlResponse>

    @POST("api/v1/communications/deliver")
    fun deliverAutomaticMessage(
        @Header("X-Firebase-ID-Token") firebaseIdToken: String,
        @Body request: AutomaticDeliveryRequest
    ): Call<AutomaticDeliveryResponse>

    @GET("api/v1/communications/status")
    fun automaticDeliveryStatus(
        @Header("X-Firebase-ID-Token") firebaseIdToken: String,
        @Query("deliveryKey") deliveryKey: String
    ): Call<AutomaticDeliveryStatusResponse>

    @GET("api/v1/subscriptions/status")
    fun accountSubscriptionStatus(
        @Header("X-Firebase-ID-Token") firebaseIdToken: String
    ): Call<AccountSubscriptionResponse>

    @POST("api/v1/subscriptions/verify")
    fun verifyAccountSubscription(
        @Header("X-Firebase-ID-Token") firebaseIdToken: String,
        @Body request: VerifySubscriptionRequest
    ): Call<AccountSubscriptionResponse>

    @GET("api/v1/reports/dashboard")
    fun dashboard(@Header("X-Organization-Id") organizationId: String): Call<DashboardResponse>

    @GET("api/v1/members")
    fun members(
        @Header("X-Organization-Id") organizationId: String,
        @Query("q") query: String = ""
    ): Call<List<MemberResponse>>
}
