package com.membora.app.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import com.membora.app.data.pretty
import com.membora.app.receipt.PosPrinter
import com.membora.app.receipt.PosPrinterDevice
import com.membora.app.receipt.ReceiptData
import com.membora.app.receipt.ReceiptOutput
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PaymentReceiptDialog(
    receipt: ReceiptData,
    onDismiss: () -> Unit,
    onMessage: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var showPos by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showPos = true else message = "Nearby devices permission is required for Bluetooth POS printing"
    }
    fun openPos() {
        if (PosPrinter.hasPermission(context)) showPos = true
        else permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }
    if (showPos) {
        PosPrinterDialog(
            receipt = receipt,
            onDismiss = { showPos = false },
            onResult = { message = it; showPos = false }
        )
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.94f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Payment receipt", style = MaterialTheme.typography.titleLarge)
                        Text(receipt.receiptNumber, color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                HorizontalDivider()
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(receipt.organizationName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text(receipt.branchName, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (receipt.organizationAddress.isNotBlank()) Text(receipt.organizationAddress, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    if (receipt.taxId.isNotBlank()) Text("Tax / GST ID: ${receipt.taxId}", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    ReceiptRow("Date", receipt.paidOn.format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")))
                    ReceiptRow("Member", receipt.memberName)
                    ReceiptRow("Member ID", receipt.memberCode)
                    if (receipt.memberPhone.isNotBlank()) ReceiptRow("Mobile", receipt.memberPhone)
                    ReceiptRow("Membership", receipt.planName)
                    if (receipt.membershipPeriod.isNotBlank()) ReceiptRow("Validity", receipt.membershipPeriod)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    ReceiptRow("Membership amount", ReceiptOutput.money(receipt, receipt.membershipAmount))
                    if (receipt.registrationFee > 0.009) ReceiptRow("Registration fee", ReceiptOutput.money(receipt, receipt.registrationFee))
                    if (receipt.discount > 0.009) ReceiptRow("Discount", "- ${ReceiptOutput.money(receipt, receipt.discount)}")
                    ReceiptRow("Membership total", ReceiptOutput.money(receipt, receipt.membershipTotal), true)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    ReceiptRow("Amount received", ReceiptOutput.money(receipt, receipt.amountReceived), true)
                    ReceiptRow("Payment method", receipt.paymentMethod.pretty())
                    if (receipt.paymentReference.isNotBlank()) ReceiptRow("Transaction reference", receipt.paymentReference)
                    ReceiptRow("Total paid", ReceiptOutput.money(receipt, receipt.cumulativePaid))
                    ReceiptRow("Balance due", ReceiptOutput.money(receipt, receipt.balanceAfterPayment), receipt.balanceAfterPayment > 0.009)
                    if (receipt.notes.isNotBlank()) ReceiptRow("Notes", receipt.notes)
                    ReceiptRow("Received by", receipt.receivedBy)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(receipt.receiptFooter, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    message?.let { Text(it, color = if (it.contains("printed", true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { ReceiptOutput.print(context, receipt) }) { Text("Print / Save PDF") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { ReceiptOutput.createPdf(context, receipt) } }
                                .onSuccess { ReceiptOutput.sharePdf(context, receipt, it) }
                                .onFailure { message = it.localizedMessage ?: "Unable to create PDF" }
                        }
                    }) { Text("Share PDF") }
                    OutlinedButton(onClick = ::openPos) { Text("POS printer") }
                    onMessage?.let { OutlinedButton(onClick = it) { Text("Message member") } }
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(0.42f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.58f), textAlign = TextAlign.End, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun PosPrinterDialog(receipt: ReceiptData, onDismiss: () -> Unit, onResult: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("membora_pos", 0) }
    var devices by remember { mutableStateOf<List<PosPrinterDevice>>(emptyList()) }
    var selectedAddress by remember { mutableStateOf(preferences.getString("printer_address", "").orEmpty()) }
    var columns by remember { mutableIntStateOf(preferences.getInt("paper_columns", 32)) }
    var openDrawer by remember { mutableStateOf(receipt.paymentMethod.name == "CASH") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { PosPrinter.pairedDevices(context) }
            .onSuccess { devices = it; if (selectedAddress.isBlank() && it.size == 1) selectedAddress = it.first().address }
            .onFailure { error = it.localizedMessage }
        loading = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thermal POS printer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pair the printer in Android Bluetooth settings first, then select it here.")
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                devices.forEach { device ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) { Text(device.name); Text(device.address, style = MaterialTheme.typography.bodySmall) }
                        RadioButton(selected = selectedAddress == device.address, onClick = { selectedAddress = device.address })
                    }
                }
                if (!loading && devices.isEmpty()) Text("No paired Bluetooth printers found", color = MaterialTheme.colorScheme.error)
                Text("Paper width", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = columns == 32, onClick = { columns = 32 }, label = { Text("58 mm") })
                    FilterChip(selected = columns == 48, onClick = { columns = 48 }, label = { Text("80 mm") })
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Open cash drawer after print")
                    Switch(checked = openDrawer, onCheckedChange = { openDrawer = it })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedAddress.isNotBlank() && !loading,
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { PosPrinter.print(context, selectedAddress, receipt, columns, openDrawer) } }
                            .onSuccess {
                                preferences.edit {
                                    putString("printer_address", selectedAddress)
                                    putInt("paper_columns", columns)
                                }
                                onResult("Receipt printed successfully")
                            }
                            .onFailure { error = it.localizedMessage ?: "POS printing failed"; loading = false }
                    }
                }
            ) { Text(if (loading) "Printing…" else "Print receipt") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
