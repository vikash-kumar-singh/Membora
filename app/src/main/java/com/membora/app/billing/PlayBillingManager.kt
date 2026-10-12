package com.membora.app.billing

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.security.MessageDigest

class PlayBillingManager(
    context: Context,
    firebaseUserId: String,
    private val onPurchased: (productId: String, purchaseToken: String) -> Unit
) : PurchasesUpdatedListener {
    var plans by mutableStateOf<List<PlayPlanOption>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private val obfuscatedAccountId = MessageDigest.getInstance("SHA-256")
        .digest(firebaseUserId.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private var productDetails: ProductDetails? = null
    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (billingClient.isReady) {
            loadProductsAndPurchases()
            return
        }
        loading = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    loadProductsAndPurchases()
                } else {
                    loading = false
                    message = billingMessage(result)
                }
            }

            override fun onBillingServiceDisconnected() {
                loading = false
                message = "Google Play billing disconnected. Check your connection and try again."
            }
        })
    }

    fun refresh() {
        message = null
        start()
    }

    fun launch(activity: Activity, plan: PlayPlanOption) {
        val details = productDetails
        if (details == null || !billingClient.isReady) {
            message = "Google Play billing is not ready yet. Try again."
            start()
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(plan.offerToken)
            .build()
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .setObfuscatedAccountId(obfuscatedAccountId)
                .build()
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            message = billingMessage(result)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach(::handlePurchase)
            BillingClient.BillingResponseCode.USER_CANCELED -> message = "Purchase cancelled. No charge was made."
            else -> message = billingMessage(result)
        }
    }

    fun close() {
        billingClient.endConnection()
    }

    private fun loadProductsAndPurchases() {
        queryPlans()
        queryOwnedSubscriptions()
    }

    private fun queryPlans() {
        loading = true
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(MEMBORA_SUBSCRIPTION_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        ) { result, detailsResult ->
            loading = false
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                message = billingMessage(result)
                return@queryProductDetailsAsync
            }
            val details = detailsResult.productDetailsList.firstOrNull()
            productDetails = details
            plans = details?.subscriptionOfferDetails.orEmpty().mapNotNull { offer ->
                val term = SubscriptionTerm.fromBasePlanId(offer.basePlanId) ?: return@mapNotNull null
                val recurringPhase = offer.pricingPhases.pricingPhaseList.lastOrNull()
                    ?: return@mapNotNull null
                PlayPlanOption(
                    term = term,
                    formattedPrice = recurringPhase.formattedPrice,
                    billingPeriod = recurringPhase.billingPeriod,
                    offerToken = offer.offerToken
                )
            }.distinctBy { it.term }.sortedBy { it.term.months }
            if (plans.isEmpty()) {
                message = "Subscription plans are not available for this Play account. Install the Play testing build or finish configuring the four base plans."
            }
        }
    }

    private fun queryOwnedSubscriptions() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach(::handlePurchase)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (purchase.products.contains(MEMBORA_SUBSCRIPTION_PRODUCT_ID)) {
                    message = "Confirming your subscription…"
                    onPurchased(MEMBORA_SUBSCRIPTION_PRODUCT_ID, purchase.purchaseToken)
                }
            }
            Purchase.PurchaseState.PENDING -> {
                message = "Payment is pending. Access starts after Google Play confirms payment."
            }
            else -> Unit
        }
    }

    private fun billingMessage(result: BillingResult): String =
        result.debugMessage.takeIf(String::isNotBlank)
            ?: "Google Play billing error (${result.responseCode})."
}
