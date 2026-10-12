package com.membora.app.billing

import com.membora.app.data.remote.AccountSubscriptionResponse
import com.membora.app.data.remote.ApiClient
import com.membora.app.data.remote.VerifySubscriptionRequest
import com.membora.app.firebase.FirebaseServices
import java.time.Instant
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object SubscriptionVerificationService {
    fun verify(
        productId: String,
        purchaseToken: String,
        onResult: (Result<AccountSubscription>) -> Unit
    ) {
        val user = FirebaseServices.auth?.currentUser
            ?: return onResult(Result.failure(IllegalStateException("Sign in before subscribing")))
        if (user.isAnonymous) {
            return onResult(Result.failure(IllegalStateException("Create an email or Google account before subscribing")))
        }
        user.getIdToken(true).addOnSuccessListener { tokenResult ->
            val idToken = tokenResult.token
            if (idToken.isNullOrBlank()) {
                onResult(Result.failure(IllegalStateException("Unable to verify your Firebase session")))
                return@addOnSuccessListener
            }
            ApiClient.api.verifyAccountSubscription(
                idToken,
                VerifySubscriptionRequest(productId, purchaseToken)
            ).enqueue(subscriptionCallback(onResult))
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun refresh(onResult: (Result<AccountSubscription>) -> Unit) {
        val user = FirebaseServices.auth?.currentUser
            ?: return onResult(Result.failure(IllegalStateException("Sign in to restore purchases")))
        user.getIdToken(true).addOnSuccessListener { tokenResult ->
            val idToken = tokenResult.token
            if (idToken.isNullOrBlank()) {
                onResult(Result.failure(IllegalStateException("Unable to verify your Firebase session")))
                return@addOnSuccessListener
            }
            ApiClient.api.accountSubscriptionStatus(idToken)
                .enqueue(subscriptionCallback(onResult))
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun subscriptionCallback(
        onResult: (Result<AccountSubscription>) -> Unit
    ) = object : Callback<AccountSubscriptionResponse> {
        override fun onResponse(
            call: Call<AccountSubscriptionResponse>,
            response: Response<AccountSubscriptionResponse>
        ) {
            val body = response.body()
            if (response.isSuccessful && body != null) {
                onResult(Result.success(body.toDomain()))
            } else {
                onResult(Result.failure(IllegalStateException(
                    "Subscription confirmation failed (HTTP ${response.code()})."
                )))
            }
        }

        override fun onFailure(call: Call<AccountSubscriptionResponse>, throwable: Throwable) {
            onResult(Result.failure(IllegalStateException(
                "Cannot reach the Membora subscription service. Check your connection and try again.",
                throwable
            )))
        }
    }
}

internal fun AccountSubscriptionResponse.toDomain() = AccountSubscription(
    entitled = entitled,
    status = status,
    productId = productId,
    basePlanId = basePlanId,
    expiresAt = expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    autoRenewing = autoRenewing,
    verifiedAt = verifiedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
)
