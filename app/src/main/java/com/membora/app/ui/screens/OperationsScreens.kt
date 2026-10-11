package com.membora.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.membora.app.data.MemboraRepository
import com.membora.app.data.asMoney
import com.membora.app.data.pretty
import com.membora.app.domain.PaymentMethod
import com.membora.app.receipt.receiptData
import com.membora.app.ui.components.*

@Composable
fun CheckInScreen(repository: MemboraRepository, onOpenMember: (String) -> Unit) {
    val context = LocalContext.current
    var search by rememberSaveable { mutableStateOf("") }
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var success by rememberSaveable { mutableStateOf(false) }
    var scannerOpen by remember { mutableStateOf(false) }
    val scannerOptions = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .allowManualInput()
            .build()
    }
    val scanner = remember(context, scannerOptions) {
        GmsBarcodeScanning.getClient(context, scannerOptions)
    }
    val today = repository.currentDate()
    val matches = if (search.isBlank()) emptyList() else repository.members.filter {
        it.fullName.contains(search, true) || it.code.contains(search, true) || it.mobile.contains(search)
    }.take(5)
    val todayVisits = repository.visits.filter { it.checkedInAt.toLocalDate() == today }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            EnterprisePageHeader("Check-in", "Verify membership access in seconds")
        }
        item {
            Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(92.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(46.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Scan member QR", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
                    Text("Point the camera at a member QR code", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            scannerOpen = true
                            scanner.startScan()
                                .addOnSuccessListener { barcode ->
                                    scannerOpen = false
                                    repository.checkInFromQr(barcode.rawValue.orEmpty()).fold(
                                        onSuccess = { visit ->
                                            val member = repository.member(visit.memberId)
                                            success = true
                                            resultMessage = "${member?.fullName ?: "Member"} checked in successfully"
                                            search = ""
                                        },
                                        onFailure = { error ->
                                            success = false
                                            resultMessage = error.message ?: "QR check-in failed"
                                        }
                                    )
                                }
                                .addOnCanceledListener { scannerOpen = false }
                                .addOnFailureListener { error ->
                                    scannerOpen = false
                                    success = false
                                    resultMessage = error.localizedMessage
                                        ?.takeIf(String::isNotBlank)
                                        ?: "Scanner unavailable. Check Google Play services and try again."
                                }
                        },
                        enabled = !scannerOpen,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f))
                    ) { Text(if (scannerOpen) "Opening camera…" else "Open scanner") }
                }
            }
        }
        item { SearchBox(search, { search = it }, "Enter member ID, name or phone") }
        resultMessage?.let { message ->
            item {
                Surface(color = if (success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, Modifier.weight(1f), color = if (success) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = { resultMessage = null }) { Text("Dismiss") }
                    }
                }
            }
        }
        if (matches.isNotEmpty()) {
            item { Text("Matching members", style = MaterialTheme.typography.titleMedium) }
            items(matches) { member ->
                AppCard {
                    MemberRow(member, repository.memberStatus(member.id), member.code, onClick = { onOpenMember(member.id) })
                    Button(
                        onClick = {
                            repository.checkIn(member.id).fold(
                                onSuccess = { success = true; resultMessage = "${member.fullName} checked in successfully"; search = "" },
                                onFailure = { success = false; resultMessage = it.message ?: "Check-in failed" }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Check in ${member.firstName}") }
                }
            }
        }
        item { SectionHeading("Today's visits", "${todayVisits.size} total") {} }
        if (todayVisits.isEmpty()) item { EmptyState("No visits yet", "Today's check-ins will appear here.") }
        else items(todayVisits) { visit ->
            val member = repository.member(visit.memberId) ?: return@items
            AppCard(onClick = { onOpenMember(member.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MemberAvatar(member)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(member.fullName, style = MaterialTheme.typography.titleMedium)
                        Text("${visit.checkedInAt.toUiDateTime()} • ${visit.method.lowercase().replaceFirstChar { it.uppercase() }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("IN", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PaymentsScreen(repository: MemboraRepository, onOpenMember: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf("All") }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var receiptPaymentId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingMessageDraft by remember { mutableStateOf<com.membora.app.domain.MemberMessageDraft?>(null) }
    val methods = listOf("All", "UPI", "Cash", "Card", "Bank transfer")
    val filtered = repository.payments.filter { payment ->
        val member = repository.member(payment.memberId)
        val searchMatch = search.isBlank() || member?.fullName?.contains(search, true) == true || payment.receiptNumber.contains(search, true)
        val methodMatch = method == "All" || payment.method.pretty().equals(method, true)
        searchMatch && methodMatch
    }
    val today = repository.currentDate()
    val todayTotal = repository.payments.filter { it.paidOn.toLocalDate() == today }.sumOf { it.amount }
    val monthTotal = repository.payments.filter { it.paidOn.year == today.year && it.paidOn.month == today.month }.sumOf { it.amount }
    val dueMembers = repository.members.mapNotNull { member ->
        val membership = repository.membershipFor(member.id) ?: return@mapNotNull null
        val due = repository.outstandingBalance(membership.id)
        if (due > 0.009 && membership.status != com.membora.app.domain.MembershipStatus.CANCELLED) Triple(member, membership, due) else null
    }.sortedByDescending { it.third }

    if (showAdd) GeneralPaymentDialog(repository, onDismiss = { showAdd = false }, onSaved = { paymentId ->
        showAdd = false
        receiptPaymentId = paymentId
    })
    receiptPaymentId?.let { paymentId ->
        runCatching { repository.receiptData(paymentId) }.getOrNull()?.let { receipt ->
            PaymentReceiptDialog(
                receipt = receipt,
                onDismiss = { receiptPaymentId = null },
                onMessage = { pendingMessageDraft = runCatching { repository.paymentConfirmationDraft(paymentId) }.getOrNull() }
            )
        }
    }
    pendingMessageDraft?.let { draft ->
        MemberMessageDialog(repository, draft) { pendingMessageDraft = null }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            EnterprisePageHeader("Payments", "Receipts, dues and collections") {
                Button(onClick = { showAdd = true }, shape = RoundedCornerShape(13.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Record")
                }
            }
        }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 480.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard("Today", "${repository.organization.currencySymbol}${todayTotal.asMoney()}", "${repository.payments.count { it.paidOn.toLocalDate() == today }} payments", MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth())
                        MetricCard("This month", "${repository.organization.currencySymbol}${monthTotal.asMoney()}", "gross collected", MaterialTheme.colorScheme.tertiary, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard("Today", "${repository.organization.currencySymbol}${todayTotal.asMoney()}", "${repository.payments.count { it.paidOn.toLocalDate() == today }} payments", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        MetricCard("This month", "${repository.organization.currencySymbol}${monthTotal.asMoney()}", "gross collected", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            SearchBox(search, { search = it }, "Search member or receipt")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                methods.forEach { item -> FilterChip(selected = method == item, onClick = { method = item }, label = { Text(item) }) }
            }
        }
        if (dueMembers.isNotEmpty()) {
            item { SectionHeading("Outstanding dues", "${dueMembers.size} member${if (dueMembers.size == 1) "" else "s"}") {} }
            items(dueMembers, key = { it.first.id }) { (member, membership, due) ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MemberAvatar(member)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(member.fullName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Due ${repository.organization.currencySymbol}${due.asMoney()} • ${repository.plan(membership.planId)?.name ?: "Membership"}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        TextButton(onClick = {
                            pendingMessageDraft = runCatching { repository.dueReminderDraft(member.id) }.getOrNull()
                        }) { Text("Remind") }
                    }
                }
            }
        }
        item { SectionHeading("Transactions", "${filtered.size} records") {} }
        if (filtered.isEmpty()) item { EmptyState("No payments found", "Change the search or record a payment.", "Record payment") { showAdd = true } }
        else items(filtered, key = { it.id }) { payment ->
            val member = repository.member(payment.memberId) ?: return@items
            AppCard(onClick = { onOpenMember(member.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InitialIcon(repository.organization.currencySymbol, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(member.fullName, style = MaterialTheme.typography.titleMedium)
                        Text("${payment.receiptNumber} • ${payment.method.pretty()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(payment.paidOn.toUiDateTime(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${repository.organization.currencySymbol}${payment.amount.asMoney()}", style = MaterialTheme.typography.titleMedium)
                        Text("Paid", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = {
                            pendingMessageDraft = runCatching { repository.paymentConfirmationDraft(payment.id) }.getOrNull()
                        }) { Text("Message") }
                        TextButton(onClick = { receiptPaymentId = payment.id }) { Text("Receipt") }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneralPaymentDialog(repository: MemboraRepository, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    var memberSearch by rememberSaveable { mutableStateOf("") }
    var selectedMemberId by rememberSaveable { mutableStateOf<String?>(null) }
    var amount by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf(PaymentMethod.UPI) }
    var reference by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedMember = selectedMemberId?.let(repository::member)
    val selectedMembership = selectedMemberId?.let(repository::membershipFor)?.takeUnless { it.status == com.membora.app.domain.MembershipStatus.CANCELLED }
    val outstanding = selectedMembership?.let { repository.outstandingBalance(it.id) } ?: 0.0
    val parsedAmount = amount.toDoubleOrNull() ?: 0.0
    val candidates = repository.members.filter { memberSearch.isNotBlank() && (it.fullName.contains(memberSearch, true) || it.mobile.contains(memberSearch)) }.take(3)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selectedMember == null) {
                    OutlinedTextField(memberSearch, { memberSearch = it }, label = { Text("Find member") }, singleLine = true)
                    candidates.forEach { member -> TextButton(onClick = { selectedMemberId = member.id; memberSearch = member.fullName }, modifier = Modifier.fillMaxWidth()) { Text("${member.fullName} • ${member.code}") } }
                } else {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.clickable { selectedMemberId = null }) {
                        Text("${selectedMember.fullName} • Change", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount") }, prefix = { Text(repository.organization.currencySymbol) }, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaymentMethod.entries.forEach { item -> FilterChip(selected = method == item, onClick = { method = item }, label = { Text(item.pretty()) }) }
                }
                if (method != PaymentMethod.CASH) OutlinedTextField(reference, { reference = it }, Modifier.fillMaxWidth(), label = { Text("Transaction / reference ID") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Receipt notes (optional)") }, minLines = 2)
                if (selectedMember != null) Text(
                    if (selectedMembership == null) "No payable membership" else "Outstanding: ${repository.organization.currencySymbol}${outstanding.asMoney()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selectedMembership == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { Button(onClick = {
            if (selectedMember != null && selectedMembership != null) {
                val paymentId = runCatching {
                    repository.recordPayment(selectedMember.id, selectedMembership.id, parsedAmount, method, notes = notes, reference = reference)
                }
                    .onFailure { error = it.localizedMessage }
                    .getOrNull()
                if (paymentId != null) onSaved(paymentId)
            }
        }, enabled = selectedMember != null && selectedMembership != null && parsedAmount > 0 && parsedAmount <= outstanding) { Text("Create receipt") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
