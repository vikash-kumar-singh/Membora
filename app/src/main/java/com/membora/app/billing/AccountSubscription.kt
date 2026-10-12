package com.membora.app.billing

import java.time.Instant

const val MEMBORA_SUBSCRIPTION_PRODUCT_ID = "membora_premium"

enum class SubscriptionTerm(
    val basePlanId: String,
    val months: Int,
    val title: String
) {
    MONTHLY("monthly", 1, "Monthly"),
    THREE_MONTHS("three-month", 3, "3 months"),
    SIX_MONTHS("six-month", 6, "6 months"),
    TWELVE_MONTHS("annual", 12, "12 months");

    companion object {
        fun fromBasePlanId(value: String?): SubscriptionTerm? =
            entries.firstOrNull { it.basePlanId == value }
    }
}

data class AccountSubscription(
    val entitled: Boolean = false,
    val status: String = "NONE",
    val productId: String = "",
    val basePlanId: String = "",
    val expiresAt: Instant? = null,
    val autoRenewing: Boolean = false,
    val verifiedAt: Instant? = null
) {
    val term: SubscriptionTerm? get() = SubscriptionTerm.fromBasePlanId(basePlanId)
    val hasAccess: Boolean get() = entitled && expiresAt?.isAfter(Instant.now()) == true
}

enum class SubscriptionLoadState { LOADING, LOADED, ERROR }

data class AccountSubscriptionState(
    val loadState: SubscriptionLoadState = SubscriptionLoadState.LOADING,
    val subscription: AccountSubscription = AccountSubscription(),
    val message: String? = null
)

data class PlayPlanOption(
    val term: SubscriptionTerm,
    val formattedPrice: String,
    val billingPeriod: String,
    internal val offerToken: String
)
