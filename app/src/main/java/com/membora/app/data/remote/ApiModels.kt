package com.membora.app.data.remote

data class HealthResponse(val status: String = "UNKNOWN")

data class ImageKitAuthResponse(
    val token: String = "",
    val expire: Long = 0,
    val signature: String = "",
    val publicKey: String = "",
    val urlEndpoint: String = "",
    val folder: String = ""
)

data class ImageKitUploadResponse(
    val fileId: String = "",
    val name: String = "",
    val url: String = "",
    val thumbnailUrl: String? = null,
    val fileType: String? = null,
    val size: Long = 0
)

data class ImageKitSignedUrlResponse(
    val url: String = "",
    val expiresAt: Long = 0
)

data class VerifySubscriptionRequest(
    val productId: String,
    val purchaseToken: String
)

data class AccountSubscriptionResponse(
    val entitled: Boolean = false,
    val status: String = "NONE",
    val productId: String = "",
    val basePlanId: String = "",
    val expiresAt: String? = null,
    val autoRenewing: Boolean = false,
    val verifiedAt: String? = null
)

data class DashboardResponse(
    val totalMembers: Long = 0,
    val activeMemberships: Long = 0,
    val expiringSoon: Long = 0,
    val expired: Long = 0,
    val todayRevenue: Double = 0.0,
    val todayVisits: Long = 0
)

data class MemberResponse(
    val id: String,
    val organizationId: String,
    val branchId: String?,
    val memberCode: String,
    val firstName: String,
    val lastName: String?,
    val mobile: String,
    val email: String?,
    val city: String?,
    val status: String,
    val joinedOn: String
)
