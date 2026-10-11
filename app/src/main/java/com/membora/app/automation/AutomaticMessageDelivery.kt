package com.membora.app.automation

import com.membora.app.data.MemboraRepository
import com.membora.app.data.remote.ApiClient
import com.membora.app.data.remote.AutomaticDeliveryRequest
import com.membora.app.firebase.FirebaseServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class AutomaticDeliveryReport(
    val submitted: Int = 0,
    val pendingConfiguration: Int = 0,
    val failed: Int = 0,
    val retryNeeded: Boolean = false
)

object AutomaticMessageDelivery {
    suspend fun deliverReady(repository: MemboraRepository): AutomaticDeliveryReport {
        if (repository.currentWorkspaceRole != "Owner") return AutomaticDeliveryReport()
        val user = FirebaseServices.auth?.currentUser ?: return AutomaticDeliveryReport()
        val token = suspendCoroutine<String?> { continuation ->
            user.getIdToken(false).addOnCompleteListener { task ->
                continuation.resume(if (task.isSuccessful) task.result?.token else null)
            }
        } ?: return AutomaticDeliveryReport(failed = 1, retryNeeded = true)

        var submitted = 0
        var pendingConfiguration = 0
        var failed = 0
        var retryNeeded = false
        repository.automaticCommunicationsReady().forEach { communication ->
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val response = ApiClient.api.deliverAutomaticMessage(
                        token,
                        AutomaticDeliveryRequest(
                            deliveryKey = communication.deliveryKey,
                            channel = communication.channel.name,
                            recipient = communication.recipient,
                            templateKey = communication.templateKey,
                            parameters = communication.templateParameters
                        )
                    ).execute()
                    if (!response.isSuccessful) {
                        error("Messaging server returned HTTP ${response.code()}")
                    }
                    response.body() ?: error("Messaging server returned an empty response")
                }
            }
            result.onSuccess { response ->
                when (response.status) {
                    "SUBMITTED", "SENT", "DELIVERED", "READ" -> {
                        if (response.status == "SUBMITTED") submitted++
                        repository.recordAutomaticDeliveryResult(
                            communication.id,
                            response.status,
                            response.providerMessageId,
                            ""
                        )
                    }
                    "NOT_CONFIGURED" -> {
                        pendingConfiguration++
                        repository.recordAutomaticDeliveryResult(
                            communication.id,
                            "READY",
                            failureReason = response.detail
                        )
                    }
                    else -> {
                        failed++
                        retryNeeded = retryNeeded || response.retryable
                        repository.recordAutomaticDeliveryResult(
                            communication.id,
                            if (response.retryable) "READY" else "FAILED",
                            failureReason = response.detail
                        )
                    }
                }
            }.onFailure { error ->
                failed++
                retryNeeded = true
                repository.recordAutomaticDeliveryResult(
                    communication.id,
                    "READY",
                    failureReason = error.message ?: "Automatic delivery failed"
                )
            }
        }

        repository.automaticCommunicationsAwaitingStatus().forEach { communication ->
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val response = ApiClient.api.automaticDeliveryStatus(
                        token,
                        communication.deliveryKey
                    ).execute()
                    if (!response.isSuccessful) {
                        error("Messaging status server returned HTTP ${response.code()}")
                    }
                    response.body() ?: error("Messaging status server returned an empty response")
                }
            }
            result.onSuccess { response ->
                when (response.status) {
                    "SENT", "DELIVERED", "READ" -> repository.recordAutomaticDeliveryResult(
                        communication.id,
                        response.status,
                        response.providerMessageId,
                        ""
                    )
                    "FAILED" -> repository.recordAutomaticDeliveryResult(
                        communication.id,
                        "FAILED",
                        response.providerMessageId,
                        response.detail.ifBlank { response.providerStatus }
                    )
                    "SUBMITTED", "PENDING" -> retryNeeded = true
                }
            }.onFailure {
                retryNeeded = true
            }
        }
        return AutomaticDeliveryReport(submitted, pendingConfiguration, failed, retryNeeded)
    }
}
