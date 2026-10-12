package com.membora.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.membora.app.billing.AccountSubscription
import com.membora.app.billing.PlayBillingManager
import com.membora.app.billing.PlayPlanOption
import com.membora.app.billing.SubscriptionTerm
import com.membora.app.billing.SubscriptionVerificationService
import com.membora.app.ui.components.BrandMark

@Composable
fun SubscriptionScreen(
    firebaseUserId: String,
    onSubscriptionConfirmed: (AccountSubscription) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var selectedTerm by remember { mutableStateOf(SubscriptionTerm.MONTHLY) }
    var confirming by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    val billing = remember(firebaseUserId) {
        PlayBillingManager(context, firebaseUserId) { productId, purchaseToken ->
            confirming = true
            resultMessage = "Google Play confirmed payment. Securing your access…"
            SubscriptionVerificationService.verify(productId, purchaseToken) { result ->
                confirming = false
                result.fold(
                    onSuccess = { subscription ->
                        if (subscription.hasAccess) {
                            resultMessage = "Subscription active. Opening your workspace…"
                            onSubscriptionConfirmed(subscription)
                        } else {
                            resultMessage = "Payment is still pending. Access will start after confirmation."
                        }
                    },
                    onFailure = { error ->
                        resultMessage = error.localizedMessage ?: "Unable to confirm subscription"
                    }
                )
            }
        }
    }

    DisposableEffect(billing) {
        billing.start()
        onDispose(billing::close)
    }

    LaunchedEffect(billing.plans) {
        if (billing.plans.none { it.term == selectedTerm }) {
            billing.plans.firstOrNull()?.let { selectedTerm = it.term }
        }
    }

    val selectedPlan = billing.plans.firstOrNull { it.term == selectedTerm }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BrandMark()
                Spacer(Modifier.height(18.dp))
                Text(
                    "Choose your Membora plan",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your 15-day trial is complete. Select a secure Google Play subscription to continue with your existing workspace.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (billing.loading) {
            item {
                CircularProgressIndicator()
                Text("Loading localized prices from Google Play…")
            }
        }

        items(billing.plans, key = { it.term.basePlanId }) { plan ->
            SubscriptionPlanCard(
                plan = plan,
                selected = selectedTerm == plan.term,
                onSelect = { selectedTerm = plan.term }
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Included with every plan", style = MaterialTheme.typography.titleMedium)
                    Text("• Complete member, payment and attendance management")
                    Text("• Receipts, reports, QR check-in and organization branding")
                    Text("• Cloud sync, staff access and automated reminders")
                    HorizontalDivider()
                    Text(
                        "Subscriptions renew automatically for the selected billing period unless cancelled in Google Play before renewal. The final localized price and taxes are shown by Google Play before you confirm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        resultMessage?.let { message ->
            item {
                Text(
                    message,
                    color = if (message.contains("active", ignoreCase = true)) {
                        MaterialTheme.colorScheme.primary
                    } else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        billing.message?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }

        item {
            Button(
                onClick = {
                    val plan = selectedPlan
                    when {
                        activity == null -> resultMessage = "Unable to open Google Play from this screen."
                        plan == null -> resultMessage = "Select an available plan first."
                        else -> billing.launch(activity, plan)
                    }
                },
                enabled = selectedPlan != null && !confirming && !billing.loading,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (confirming) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(selectedPlan?.let { "Continue with ${it.term.title} • ${it.formattedPrice}" } ?: "Choose a plan")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    resultMessage = "Checking Google Play for an existing subscription…"
                    billing.refresh()
                    SubscriptionVerificationService.refresh { result ->
                        result.onSuccess { subscription ->
                            if (subscription.hasAccess) onSubscriptionConfirmed(subscription)
                            else resultMessage = "No active Membora subscription was found for this account."
                        }.onFailure { error ->
                            resultMessage = error.localizedMessage ?: "Unable to restore subscription"
                        }
                    }
                },
                enabled = !confirming,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Restore purchase") }
            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text("Use another account")
            }
        }
    }
}

@Composable
private fun SubscriptionPlanCard(
    plan: PlayPlanOption,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = if (selected) 3.dp else 0.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(plan.term.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    when (plan.term) {
                        SubscriptionTerm.MONTHLY -> "Maximum flexibility"
                        SubscriptionTerm.THREE_MONTHS -> "Quarterly billing"
                        SubscriptionTerm.SIX_MONTHS -> "Better long-term value"
                        SubscriptionTerm.TWELVE_MONTHS -> "Best annual value"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(plan.formattedPrice, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
