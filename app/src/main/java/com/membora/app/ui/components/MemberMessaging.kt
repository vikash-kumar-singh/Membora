package com.membora.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.membora.app.data.MemboraRepository
import com.membora.app.domain.CommunicationChannel
import com.membora.app.domain.MemberMessageDraft

@Composable
fun MemberMessageDialog(
    repository: MemboraRepository,
    draft: MemberMessageDraft,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var message by remember(draft) { mutableStateOf(draft.message) }
    var error by remember(draft) { mutableStateOf<String?>(null) }

    fun open(channel: CommunicationChannel) {
        val result = when (channel) {
            CommunicationChannel.WHATSAPP -> openWhatsApp(
                context = context,
                phone = draft.recipient,
                countryCode = repository.organization.countryCode,
                message = message
            )
            CommunicationChannel.SMS -> openSms(context, draft.recipient, message)
            else -> Result.failure(IllegalArgumentException("Unsupported messaging channel"))
        }
        result.fold(
            onSuccess = {
                repository.recordExternalCommunication(draft, channel, message)
                onDismiss()
            },
            onFailure = { error = it.localizedMessage ?: "Unable to open the messaging app" }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(draft.title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "To: ${draft.recipient}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Message") },
                    minLines = 6,
                    maxLines = 12
                )
                Text(
                    "The selected app opens with this message. Review it there, then tap Send.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { open(CommunicationChannel.SMS) }, enabled = message.isNotBlank()) {
                    Text("SIM SMS")
                }
                Button(onClick = { open(CommunicationChannel.WHATSAPP) }, enabled = message.isNotBlank()) {
                    Text("WhatsApp")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}

private fun openSms(context: Context, phone: String, message: String): Result<Unit> = runCatching {
    require(phone.count(Char::isDigit) >= 8) { "This member does not have a valid mobile number" }
    context.startActivity(
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(phone)}"))
            .putExtra("sms_body", message)
    )
}

private fun openWhatsApp(
    context: Context,
    phone: String,
    countryCode: String,
    message: String
): Result<Unit> = runCatching {
    val number = whatsappNumber(phone, countryCode)
    require(number.length >= 10) { "This member does not have a valid WhatsApp number" }
    val uri = Uri.parse("https://wa.me/$number?text=${Uri.encode(message)}")
    val baseIntent = Intent(Intent.ACTION_VIEW, uri)
    val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
    val opened = packages.any { packageName ->
        runCatching {
            context.startActivity(Intent(baseIntent).setPackage(packageName))
            true
        }.getOrDefault(false)
    }
    if (!opened) context.startActivity(baseIntent)
}

private fun whatsappNumber(phone: String, countryCode: String): String {
    val trimmed = phone.trim()
    val digits = trimmed.filter(Char::isDigit).removePrefix("00")
    if (trimmed.startsWith("+") || trimmed.startsWith("00")) return digits
    val callingCode = mapOf(
        "IN" to "91", "US" to "1", "CA" to "1", "GB" to "44", "AE" to "971",
        "AU" to "61", "SG" to "65", "MY" to "60", "NZ" to "64", "ZA" to "27"
    )[countryCode.uppercase()].orEmpty()
    return if (callingCode.isNotBlank() && digits.length <= 10) callingCode + digits else digits
}
