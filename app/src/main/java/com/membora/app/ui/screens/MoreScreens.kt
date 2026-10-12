package com.membora.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.membora.app.data.MemboraRepository
import com.membora.app.LegalLinks
import com.membora.app.billing.AccountSubscription
import com.membora.app.billing.MEMBORA_SUBSCRIPTION_PRODUCT_ID
import com.membora.app.data.WorkspacePermission
import com.membora.app.automation.AutomationScheduler
import com.membora.app.data.DataSyncStatus
import com.membora.app.data.UploadCompression
import com.membora.app.data.asMoney
import com.membora.app.data.pretty
import com.membora.app.domain.MemberStatus
import com.membora.app.domain.PaymentMethod
import com.membora.app.ui.components.*
import coil3.compose.AsyncImage
import java.time.ZoneId
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MoreScreen(
    repository: MemboraRepository,
    onPlans: () -> Unit,
    onServices: () -> Unit,
    onReports: () -> Unit,
    onAutomations: () -> Unit,
    onStaff: () -> Unit,
    onSettings: () -> Unit,
    isGuestSession: Boolean = false,
    guestTrialDaysRemaining: Int? = null,
    accountSubscription: AccountSubscription? = null,
    onUpgradeGuest: () -> Unit = {},
    onSignOut: () -> Unit
) {
    var syncMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showGuestExitWarning by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    if (showGuestExitWarning) {
        AlertDialog(
            onDismissRequest = { showGuestExitWarning = false },
            title = { Text("Exit guest workspace?") },
            text = {
                Text("This workspace cannot be recovered on another device after you sign out. Create an account first if you want to keep access to its data.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGuestExitWarning = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Exit guest workspace") }
            },
            dismissButton = {
                TextButton(onClick = { showGuestExitWarning = false }) { Text("Keep workspace") }
            }
        )
    }
    val items = buildList {
        if (repository.can(WorkspacePermission.MANAGE_CATALOG)) {
            add(MoreItem(Icons.Rounded.AccountTree, "Plans", "Durations, prices and bundled services", MaterialTheme.colorScheme.primary, onPlans))
            add(MoreItem(Icons.Rounded.Stars, "Services & benefits", "Configurable entitlements and add-ons", MaterialTheme.colorScheme.tertiary, onServices))
        }
        if (repository.can(WorkspacePermission.VIEW_REPORTS)) {
            add(MoreItem(Icons.Rounded.BarChart, "Reports", "Revenue, members and attendance", Color(0xFF6A5ACD), onReports))
        }
        if (repository.can(WorkspacePermission.MANAGE_AUTOMATIONS)) {
            add(MoreItem(Icons.Rounded.AutoAwesome, "Automations", "Expiry reminders and message templates", MaterialTheme.colorScheme.secondary, onAutomations))
        }
        if (repository.can(WorkspacePermission.MANAGE_STAFF)) {
            add(MoreItem(Icons.Rounded.Groups, "Staff & access", "Roles and organization permissions", Color(0xFFB34F7B), onStaff))
        }
        if (repository.can(WorkspacePermission.MANAGE_ORGANIZATION)) {
            add(MoreItem(Icons.Rounded.Settings, "Organization", "Profile, terminology and branch settings", Color(0xFF4C718F), onSettings))
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            EnterprisePageHeader("Workspace", "Configure ${repository.organization.name}")
            Spacer(Modifier.height(14.dp))
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (repository.organization.logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = repository.organization.logoUrl,
                            contentDescription = "${repository.organization.name} logo",
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        BrandMark(small = true)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(repository.organization.name, style = MaterialTheme.typography.titleMedium)
                        Text("${repository.organization.businessType} • ${repository.organization.branchName}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(repository.currentWorkspaceRole, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (isGuestSession) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("Guest workspace", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        guestTrialDaysRemaining?.let { days ->
                            Text(
                                if (days == 1) "1 day remaining in your guest trial"
                                else "$days days remaining in your guest trial",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            "Guest access ends after 15 days. Create an account with email or Google to keep access and use this workspace on another device.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onUpgradeGuest, modifier = Modifier.fillMaxWidth()) {
                            Text("Create account and keep this workspace")
                        }
                    }
                }
            }
        }
        accountSubscription?.let { subscription ->
            item {
                AppCard {
                    Text("Membora subscription", style = MaterialTheme.typography.titleMedium)
                    Text(
                        subscription.term?.let { "${it.title} plan" } ?: "Active plan",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        buildString {
                            append(if (subscription.autoRenewing) "Renews" else "Access until")
                            subscription.expiresAt?.let { append(" ${it.toString().substringBefore('T')}") }
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            uriHandler.openUri(
                                "https://play.google.com/store/account/subscriptions" +
                                    "?sku=$MEMBORA_SUBSCRIPTION_PRODUCT_ID&package=com.membora.app"
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Manage or cancel in Google Play") }
                }
            }
        }
        item {
            val sync = repository.syncDetails
            AppCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Database sync", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (sync.status) {
                                DataSyncStatus.SYNCED -> "All changes saved to Firestore"
                                DataSyncStatus.SYNCING -> "Uploading or downloading changes"
                                DataSyncStatus.CONNECTING -> "Connecting real-time listeners"
                                DataSyncStatus.OFFLINE -> "Offline — using saved device data"
                                DataSyncStatus.ERROR -> "Sync needs attention"
                                DataSyncStatus.LOCAL_ONLY -> "Local workspace — Firebase sign-in required"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = when (sync.status) {
                            DataSyncStatus.SYNCED -> MaterialTheme.colorScheme.primaryContainer
                            DataSyncStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(sync.status.pretty(), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(14.dp))
                ReportLine("Data source", if (sync.usingCachedData) "Device cache" else if (sync.status == DataSyncStatus.LOCAL_ONLY) "Local demo" else "Firestore server")
                ReportLine("Real-time listeners", "${sync.activeListeners}/${sync.expectedListeners}")
                ReportLine("Pending writes", sync.pendingWrites.toString())
                ReportLine("Last sync attempt", sync.lastAttemptAt?.toUiDateTime() ?: "Not yet")
                ReportLine("Last server sync", sync.lastServerSyncAt?.toUiDateTime() ?: "Not yet", last = true)
                if (sync.manualSyncInProgress || sync.status == DataSyncStatus.CONNECTING) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                sync.errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                syncMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        syncMessage = "Synchronizing…"
                        repository.syncNow { result ->
                            syncMessage = result.fold(
                                { "Sync complete — server confirmed all pending changes" },
                                { _ -> "Sync stopped — check the issue above, then tap Retry sync." }
                            )
                        }
                    },
                    enabled = !sync.manualSyncInProgress,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Sync, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (sync.status in setOf(DataSyncStatus.OFFLINE, DataSyncStatus.ERROR)) "Retry sync" else "Sync now")
                }
            }
        }
        items(items) { item ->
            AppCard(onClick = item.onClick) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(item.color.copy(alpha = 0.11f)), contentAlignment = Alignment.Center) {
                        Icon(item.icon, contentDescription = null, tint = item.color, modifier = Modifier.size(21.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            AppCard {
                Text("Privacy & account", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Review how data is handled or request permanent deletion of your account and associated data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { uriHandler.openUri(LegalLinks.PRIVACY_POLICY) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.PrivacyTip, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Privacy policy")
                }
                TextButton(
                    onClick = { uriHandler.openUri(LegalLinks.ACCOUNT_DELETION) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete account and data")
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { if (isGuestSession) showGuestExitWarning = true else onSignOut() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isGuestSession) "Exit guest workspace" else "Sign out")
            }
        }
        item {
            Text("Membora workspace • API-ready prototype", modifier = Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class MoreItem(val icon: ImageVector, val title: String, val subtitle: String, val color: Color, val onClick: () -> Unit)

@Composable
fun PlansScreen(repository: MemboraRepository, onBack: () -> Unit) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    if (showAdd) AddPlanDialog(repository, onDismiss = { showAdd = false })
    Scaffold(topBar = { DetailTopBar("Membership plans", "${repository.plans.size} configured", onBack) }, floatingActionButton = { ExtendedFloatingActionButton(onClick = { showAdd = true }, icon = { Text("+") }, text = { Text("New plan") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Plans define the offer. A member's purchased membership is a separate historical record.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(repository.plans, key = { it.id }) { plan ->
                AppCard {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(plan.name, style = MaterialTheme.typography.titleLarge)
                            Text(plan.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(shape = RoundedCornerShape(99.dp), color = if (plan.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) { Text(if (plan.active) "Active" else "Paused", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${repository.organization.currencySymbol}${plan.price.asMoney()}", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(8.dp))
                        Text("/ ${plan.durationValue} ${plan.durationUnit.lowercase()}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    if (plan.registrationFee > 0) Text("+ ${repository.organization.currencySymbol}${plan.registrationFee.asMoney()} registration", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${plan.serviceIds.size} bundled services", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { repository.setPlanActive(plan.id, !plan.active) }) {
                            Text(if (plan.active) "Pause plan" else "Activate plan")
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(70.dp)) }
        }
    }
}

@Composable
private fun AddPlanDialog(repository: MemboraRepository, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf("1") }
    var durationUnit by rememberSaveable { mutableStateOf("MONTH") }
    var price by rememberSaveable { mutableStateOf("") }
    var registrationFee by rememberSaveable { mutableStateOf("0") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create membership plan") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Plan name") }, singleLine = true)
            OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 2)
            OutlinedTextField(duration, { duration = it.filter(Char::isDigit) }, label = { Text("Duration") }, singleLine = true)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("DAY", "WEEK", "MONTH", "YEAR").forEach { unit ->
                    FilterChip(selected = durationUnit == unit, onClick = { durationUnit = unit }, label = { Text(unit.lowercase().replaceFirstChar(Char::uppercase)) })
                }
            }
            OutlinedTextField(price, { price = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Price") }, prefix = { Text(repository.organization.currencySymbol) }, singleLine = true)
            OutlinedTextField(registrationFee, { registrationFee = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Registration fee") }, prefix = { Text(repository.organization.currencySymbol) }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        } },
        confirmButton = { Button(onClick = {
            error = repository.addPlan(
                name = name,
                description = description,
                duration = duration.toIntOrNull() ?: 0,
                durationUnit = durationUnit,
                price = price.toDoubleOrNull() ?: -1.0,
                registrationFee = registrationFee.toDoubleOrNull() ?: -1.0
            ).exceptionOrNull()?.localizedMessage
            if (error == null) onDismiss()
        }, enabled = name.isNotBlank() && duration.toIntOrNull() != null && price.toDoubleOrNull() != null) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ServicesScreen(repository: MemboraRepository, onBack: () -> Unit) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    if (showAdd) AddServiceDialog(repository) { showAdd = false }
    Scaffold(topBar = { DetailTopBar("Services & benefits", "Reusable entitlements", onBack) }, floatingActionButton = { ExtendedFloatingActionButton(onClick = { showAdd = true }, icon = { Text("+") }, text = { Text("New service") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Attach services to plans or grant them directly. Quantities become trackable entitlements.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(repository.services, key = { it.id }) { service ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InitialIcon(service.name, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(service.name, style = MaterialTheme.typography.titleMedium)
                            Text(service.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(5.dp))
                            Text("${service.category} • measured in ${service.unitLabel}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(70.dp)) }
        }
    }
}

@Composable
private fun AddServiceDialog(repository: MemboraRepository, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create service") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(name, { name = it }, label = { Text("Service name") }); OutlinedTextField(category, { category = it }, label = { Text("Category") }) } },
        confirmButton = { Button(onClick = { repository.addService(name, category.ifBlank { "General" }); onDismiss() }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ReportsScreen(repository: MemboraRepository, onBack: () -> Unit) {
    val active = repository.members.count { repository.memberStatus(it.id) in listOf(MemberStatus.ACTIVE, MemberStatus.EXPIRING_SOON) }
    val expired = repository.members.size - active
    val revenueByMethod = PaymentMethod.entries.associateWith { method -> repository.payments.filter { it.method == method }.sumOf { it.amount } }.filterValues { it > 0 }
    val maxRevenue = revenueByMethod.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    Scaffold(topBar = { DetailTopBar("Reports", "Live organization insights", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 480.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard("Members", repository.members.size.toString(), "$active active", MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth())
                            MetricCard("Expired", expired.toString(), "need re-engagement", MaterialTheme.colorScheme.error, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard("Members", repository.members.size.toString(), "$active active", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                            MetricCard("Expired", expired.toString(), "need re-engagement", MaterialTheme.colorScheme.error, Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                SectionHeading("Revenue by method")
                AppCard {
                    revenueByMethod.forEach { (method, amount) ->
                        Text(method.pretty(), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                Box(Modifier.fillMaxWidth((amount / maxRevenue).toFloat()).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("${repository.organization.currencySymbol}${amount.asMoney()}", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
            item {
                SectionHeading("Plan popularity")
                AppCard {
                    repository.plans.forEachIndexed { index, plan ->
                        val count = repository.memberships.count { it.planId == plan.id }
                        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                            Text("${index + 1}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                            Text(plan.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text("$count members", style = MaterialTheme.typography.labelLarge)
                        }
                        if (index != repository.plans.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
            }
            item {
                SectionHeading("Operating snapshot")
                AppCard {
                    ReportLine("Renewal opportunity", "${repository.members.count { repository.memberStatus(it.id) in listOf(MemberStatus.EXPIRED, MemberStatus.EXPIRING_SOON) }} members")
                    ReportLine("Visits today", repository.visits.count { it.checkedInAt.toLocalDate() == repository.currentDate() }.toString())
                    ReportLine("Average payment", "${repository.organization.currencySymbol}${(repository.payments.map { it.amount }.average().takeIf { !it.isNaN() } ?: 0.0).asMoney()}", last = true)
                }
            }
        }
    }
}

@Composable
private fun ReportLine(label: String, value: String, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp)) { Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.Bold) }
    if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}

@Composable
fun AutomationsScreen(repository: MemboraRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    var runMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingMessageDraft by remember { mutableStateOf<com.membora.app.domain.MemberMessageDraft?>(null) }
    LaunchedEffect(Unit) { repository.processDueReminders() }
    pendingMessageDraft?.let { draft ->
        MemberMessageDialog(repository, draft) { pendingMessageDraft = null }
    }
    Scaffold(topBar = { DetailTopBar("Automations", "Triggers, rules and templates", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Membership reminder engine", style = MaterialTheme.typography.titleMedium)
                        Text("Due WhatsApp and SMS reminders are submitted automatically through the configured provider. Manual sending remains available as a fallback.", color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text("Automatic checks active", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    "Runs on data changes, app startup, and every 12 hours in the background.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Last check: ${repository.lastAutomationCheckAt?.toUiDateTime() ?: "Waiting for synchronized data"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {
                            val count = repository.processDueReminders()
                            AutomationScheduler.runNow(context)
                            runMessage = if (count == 0) {
                                "Checking queued reminders for automatic delivery"
                            } else {
                                "$count reminder${if (count == 1) "" else "s"} prepared for automatic delivery"
                            }
                        }) { Text("Run reminder check") }
                        runMessage?.let { Text(it, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            items(repository.automationRules, key = { it.id }) { rule ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InitialIcon(rule.channel.name, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(rule.name, style = MaterialTheme.typography.titleMedium)
                            Text("${rule.event.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }} • ${rule.timing}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = rule.enabled, onCheckedChange = { repository.toggleRule(rule.id) })
                    }
                    Spacer(Modifier.height(12.dp))
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) { Text(rule.template, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(8.dp))
                    Text("Preferred channel", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            com.membora.app.domain.CommunicationChannel.WHATSAPP,
                            com.membora.app.domain.CommunicationChannel.SMS,
                            com.membora.app.domain.CommunicationChannel.PUSH
                        ).forEach { channel ->
                            FilterChip(
                                selected = rule.channel == channel,
                                onClick = { repository.updateRuleChannel(rule.id, channel) },
                                label = { Text(channel.pretty()) }
                            )
                        }
                    }
                }
            }
            item {
                SectionHeading("Communication history")
                Text("${repository.communications.size} recorded messages", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (repository.communications.isEmpty()) {
                item { EmptyState("No communication history", "Due reminders and member messages will appear here.") }
            } else {
                items(repository.communications.sortedByDescending { it.sentAt }.take(30), key = { it.id }) { communication ->
                    val member = repository.member(communication.memberId)
                    AppCard {
                        Row(verticalAlignment = Alignment.Top) {
                            InitialIcon(communication.channel.name, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(communication.title, style = MaterialTheme.typography.titleMedium)
                                Text(member?.fullName ?: "Unknown member", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(4.dp))
                                Text(communication.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(6.dp))
                                Text("${communication.channel.pretty()} • ${communication.status.lowercase().replaceFirstChar(Char::uppercase)} • ${communication.sentAt.toUiDateTime()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (communication.failureReason.isNotBlank()) {
                                    Text(communication.failureReason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                                if (member != null && member.mobile.isNotBlank()) {
                                    TextButton(onClick = {
                                        pendingMessageDraft = com.membora.app.domain.MemberMessageDraft(
                                            memberId = member.id,
                                            recipient = member.mobile,
                                            title = communication.title,
                                            message = communication.message,
                                            membershipId = communication.membershipId
                                        )
                                    }) { Text("Open in WhatsApp / SMS") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StaffScreen(repository: MemboraRepository, onBack: () -> Unit) {
    var showInvite by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    if (showInvite) {
        InviteStaffDialog(
            repository = repository,
            onDismiss = { showInvite = false },
            onInvited = {
                showInvite = false
                message = "Invitation email sent"
            }
        )
    }
    Scaffold(
        topBar = { DetailTopBar("Staff & access", "Role-based permissions", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showInvite = true },
                icon = { Text("+") },
                text = { Text("Invite staff") }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Invite staff by email, assign least-privilege roles, and disable access without deleting audit history.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
            items(repository.staff, key = { it.id }) { staff ->
                AppCard {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val stackRole = maxWidth < 460.dp
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Text(staff.name.split(" ").filter(String::isNotBlank).take(2).joinToString("") { it.first().toString() }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(staff.name, style = MaterialTheme.typography.titleMedium)
                                Text(staff.email.ifBlank { staff.phone }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(staff.branchName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (stackRole) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("${staff.role} • ${staff.invitationStatus.lowercase().replaceFirstChar(Char::uppercase)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            if (!stackRole) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(staff.role, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                    Text(staff.invitationStatus.lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = staff.active,
                                onCheckedChange = { enabled ->
                                    repository.setStaffActive(staff.id, enabled).onFailure {
                                        message = it.localizedMessage ?: "Unable to change access"
                                    }
                                },
                                enabled = staff.role != "Owner" && staff.firebaseUid.isNotBlank()
                            )
                        }
                        if (staff.invitationStatus in setOf("PENDING", "SENT", "DELIVERY_FAILED")) {
                            TextButton(onClick = {
                                message = "Sending invitation…"
                                repository.resendStaffInvitation(staff.id) { result ->
                                    message = result.fold(
                                        { "Invitation email resent to ${staff.email}" },
                                        { it.localizedMessage ?: "Unable to resend invitation" }
                                    )
                                }
                            }) { Text(if (staff.invitationStatus == "DELIVERY_FAILED") "Retry email" else "Resend invitation") }
                        }
                    }
                }
            }
            item {
                SectionHeading("Permission model")
                AppCard {
                    val permissions = listOf(
                        "Owner" to "Full control, security and billing",
                        "Administrator" to "Workspace configuration and staff",
                        "Manager" to "Members, plans, payments and reports",
                        "Front Desk" to "Registration, payments and check-in",
                        "Accountant" to "Payments, receipts and financial reports",
                        "Viewer" to "Read-only operational reports"
                    )
                    permissions.forEachIndexed { index, pair ->
                        ReportLine(pair.first, pair.second, index == permissions.lastIndex)
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun InviteStaffDialog(repository: MemboraRepository, onDismiss: () -> Unit, onInvited: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var branch by rememberSaveable { mutableStateOf(repository.organization.branchName) }
    var role by rememberSaveable { mutableStateOf("Front Desk") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var sending by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite staff member") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("A secure sign-in link will be emailed to this address and expires in 7 days.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it; error = null }, Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true)
                OutlinedTextField(email, { email = it; error = null }, Modifier.fillMaxWidth(), label = { Text("Work email") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Phone") }, singleLine = true)
                OutlinedTextField(branch, { branch = it }, Modifier.fillMaxWidth(), label = { Text("Branch") }, singleLine = true)
                Text("Role", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repository.staffRoles.filterNot { it == "Owner" }.forEach { item ->
                        FilterChip(selected = role == item, onClick = { role = item }, label = { Text(item) })
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = !sending,
                onClick = {
                    sending = true
                    error = null
                    repository.inviteStaff(name, email, phone, role, branch) { result ->
                        sending = false
                        result.fold(
                            onSuccess = { onInvited() },
                            onFailure = { error = it.localizedMessage ?: "Unable to invite staff" }
                        )
                    }
                }
            ) {
                if (sending) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (sending) "Sending…" else "Send invitation")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun OrganizationSettingsScreen(repository: MemboraRepository, onBack: () -> Unit, setupMode: Boolean = false) {
    var name by rememberSaveable { mutableStateOf(if (setupMode) "" else repository.organization.name) }
    var businessType by rememberSaveable { mutableStateOf(if (setupMode) repository.businessTypes.first() else repository.organization.businessType) }
    var memberLabel by rememberSaveable { mutableStateOf(repository.organization.memberLabel) }
    var phone by rememberSaveable { mutableStateOf(if (setupMode) "" else repository.organization.phone) }
    var email by rememberSaveable { mutableStateOf(if (setupMode) "" else repository.organization.email) }
    var currencyCode by rememberSaveable { mutableStateOf(repository.organization.currencyCode) }
    var timezone by rememberSaveable { mutableStateOf(repository.organization.timezone) }
    var branchName by rememberSaveable { mutableStateOf(if (setupMode) "" else repository.organization.branchName) }
    var addressLine1 by rememberSaveable { mutableStateOf(if (setupMode) "" else repository.organization.addressLine1) }
    var addressLine2 by rememberSaveable { mutableStateOf(if (setupMode) "" else repository.organization.addressLine2) }
    var city by rememberSaveable { mutableStateOf(if (setupMode) "" else repository.organization.city) }
    var state by rememberSaveable { mutableStateOf(if (setupMode) "" else repository.organization.state) }
    var postalCode by rememberSaveable { mutableStateOf(if (setupMode) "" else repository.organization.postalCode) }
    var countryCode by rememberSaveable { mutableStateOf(repository.organization.countryCode) }
    var taxId by rememberSaveable { mutableStateOf(repository.organization.taxId) }
    var receiptFooter by rememberSaveable { mutableStateOf(repository.organization.receiptFooter) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var saveError by rememberSaveable { mutableStateOf<String?>(null) }
    var logoUploading by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uploadCroppedLogo: (android.net.Uri) -> Unit = { uri ->
        logoUploading = true
        saveError = null
        scope.launch {
            val prepared = withContext(Dispatchers.IO) {
                UploadCompression.preparePhoto(context.contentResolver, uri)
            }
            prepared.fold(
                onSuccess = { upload ->
                    repository.uploadOrganizationLogo(
                        bytes = upload.bytes,
                        contentType = upload.contentType
                    ) { result ->
                        logoUploading = false
                        result.onFailure { saveError = it.localizedMessage ?: "Unable to upload logo" }
                    }
                },
                onFailure = {
                    logoUploading = false
                    saveError = it.localizedMessage ?: "Unable to prepare logo"
                }
            )
        }
    }
    val cropLogo = rememberSquareImageCropper(
        title = "Crop organization logo",
        shape = ImageCropShape.ROUNDED_SQUARE,
        onCropped = uploadCroppedLogo,
        onError = { saveError = it }
    )
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(cropLogo)
    }
    val normalizedCurrency = currencyCode.trim().uppercase(Locale.ROOT)
    val currency = remember(normalizedCurrency) { runCatching { Currency.getInstance(normalizedCurrency) }.getOrNull() }
    val normalizedTimezone = timezone.trim()
    val validTimezone = remember(normalizedTimezone) { runCatching { ZoneId.of(normalizedTimezone) }.isSuccess }
    val normalizedCountry = countryCode.trim().uppercase(Locale.ROOT)
    val validEmail = email.isBlank() || email.trim().matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
    val setupFieldsComplete = !setupMode || (email.isNotBlank() && addressLine1.isNotBlank() && city.isNotBlank())
    val canSave = name.isNotBlank() && businessType.isNotBlank() && branchName.isNotBlank() &&
        currency != null && validTimezone && normalizedCountry.length == 2 && validEmail && setupFieldsComplete
    fun changed() {
        saved = false
        saveError = null
    }
    Scaffold(
        topBar = { DetailTopBar(if (setupMode) "Create organization" else "Organization profile", if (setupMode) "Set up your workspace" else "Identity, address and regional settings", onBack) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            val result = if (setupMode) {
                                repository.createOrganization(
                                    name = name,
                                    businessType = businessType,
                                    email = email,
                                    phone = phone,
                                    branchName = branchName,
                                    addressLine1 = addressLine1,
                                    addressLine2 = addressLine2,
                                    city = city,
                                    state = state,
                                    postalCode = postalCode,
                                    countryCode = normalizedCountry,
                                    currencyCode = normalizedCurrency,
                                    timezone = normalizedTimezone
                                )
                            } else {
                                repository.updateOrganization(
                                    name = name,
                                    businessType = businessType,
                                    memberLabel = memberLabel,
                                    phone = phone,
                                    email = email,
                                    currencyCode = normalizedCurrency,
                                    timezone = normalizedTimezone,
                                    branchName = branchName,
                                    addressLine1 = addressLine1,
                                    addressLine2 = addressLine2,
                                    city = city,
                                    state = state,
                                    postalCode = postalCode,
                                    countryCode = normalizedCountry,
                                    taxId = taxId,
                                    receiptFooter = receiptFooter
                                )
                            }
                            result.fold(
                                onSuccess = { saved = true; saveError = null },
                                onFailure = { saved = false; saveError = it.localizedMessage ?: "Unable to save settings" }
                            )
                        },
                        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(20.dp).height(52.dp),
                        enabled = canSave,
                        shape = RoundedCornerShape(18.dp)
                    ) { Text(if (saved) "Saved" else if (setupMode) "Create organization" else "Save changes") }
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(PagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Workspace identity", style = MaterialTheme.typography.titleLarge)
                Text("This identity appears on member communications, receipts and staff workspaces.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                AppCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (!setupMode && repository.organization.logoUrl.isNotBlank()) {
                            AsyncImage(
                                model = repository.organization.logoUrl,
                                contentDescription = "Organization logo",
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp))
                            )
                        } else {
                            Box(Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Text(name.take(1).uppercase().ifBlank { "M" }, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Organization logo", style = MaterialTheme.typography.titleMedium)
                            Text("Compressed before secure upload", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        OutlinedButton(onClick = { logoPicker.launch("image/*") }, enabled = !logoUploading && !setupMode) {
                            Text(if (setupMode) "After setup" else if (logoUploading) "Uploading…" else "Choose")
                        }
                    }
                }
            }
            item { OutlinedTextField(name, { name = it; changed() }, Modifier.fillMaxWidth(), label = { Text("Organization name") }) }
            item {
                OutlinedTextField(businessType, { businessType = it; changed() }, Modifier.fillMaxWidth(), label = { Text("Business type") }, supportingText = { Text("Choose a category or enter a custom industry") })
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repository.businessTypes.forEach { type ->
                        FilterChip(selected = businessType == type, onClick = { businessType = type; changed() }, label = { Text(type) })
                    }
                }
            }
            item { OutlinedTextField(memberLabel, { memberLabel = it; changed() }, Modifier.fillMaxWidth(), label = { Text("People label") }, supportingText = { Text("Examples: Member, Student, Player, Client") }) }
            item { OutlinedTextField(phone, { phone = it; changed() }, Modifier.fillMaxWidth(), label = { Text("Phone") }) }
            item {
                OutlinedTextField(
                    email,
                    { email = it; changed() },
                    Modifier.fillMaxWidth(),
                    label = { Text(if (setupMode) "Organization email *" else "Organization email") },
                    isError = !validEmail,
                    supportingText = if (!validEmail) ({ Text("Enter a valid email address") }) else null
                )
            }
            item {
                SectionHeading("Business address")
                AppCard {
                    OutlinedTextField(addressLine1, { addressLine1 = it; changed() }, Modifier.fillMaxWidth(), label = { Text(if (setupMode) "Address line 1 *" else "Address line 1") })
                    OutlinedTextField(addressLine2, { addressLine2 = it; changed() }, Modifier.fillMaxWidth(), label = { Text("Address line 2") })
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth < 520.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(city, { city = it; changed() }, Modifier.fillMaxWidth(), label = { Text(if (setupMode) "City *" else "City") })
                                OutlinedTextField(state, { state = it; changed() }, Modifier.fillMaxWidth(), label = { Text("State / province") })
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(city, { city = it; changed() }, Modifier.weight(1f), label = { Text(if (setupMode) "City *" else "City") })
                                OutlinedTextField(state, { state = it; changed() }, Modifier.weight(1f), label = { Text("State / province") })
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(postalCode, { postalCode = it; changed() }, Modifier.weight(1f), label = { Text("Postal code") }, singleLine = true)
                        OutlinedTextField(normalizedCountry, { countryCode = it.filter(Char::isLetter).take(2).uppercase(Locale.ROOT); changed() }, Modifier.weight(1f), label = { Text("Country code") }, singleLine = true, isError = normalizedCountry.length != 2)
                    }
                }
            }
            item {
                SectionHeading("Regional settings")
                AppCard {
                    OutlinedTextField(
                        value = branchName,
                        onValueChange = { branchName = it; changed() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Primary branch") },
                        singleLine = true,
                        isError = branchName.isBlank(),
                        supportingText = if (branchName.isBlank()) ({ Text("Branch name is required") }) else null
                    )
                    OutlinedTextField(
                        value = normalizedCurrency,
                        onValueChange = { currencyCode = it.filter(Char::isLetter).take(3).uppercase(Locale.ROOT); changed() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Currency code") },
                        singleLine = true,
                        isError = currency == null,
                        supportingText = {
                            Text(currency?.let { "${it.displayName} • ${it.getSymbol(Locale.getDefault())}" } ?: "Enter a valid 3-letter ISO code")
                        }
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("INR", "USD", "EUR", "GBP", "AED", "AUD", "CAD", "SGD").forEach { code ->
                            FilterChip(selected = normalizedCurrency == code, onClick = { currencyCode = code; changed() }, label = { Text(code) })
                        }
                    }
                    OutlinedTextField(
                        value = timezone,
                        onValueChange = { timezone = it; changed() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Timezone") },
                        singleLine = true,
                        isError = !validTimezone,
                        supportingText = { Text(if (validTimezone) "IANA timezone used for dates, payments and reports" else "Enter a valid timezone, for example Asia/Kolkata") }
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Asia/Kolkata", "Asia/Dubai", "Europe/London", "America/New_York", "America/Los_Angeles").forEach { zone ->
                            FilterChip(selected = normalizedTimezone == zone, onClick = { timezone = zone; changed() }, label = { Text(zone.substringAfterLast('/').replace('_', ' ')) })
                        }
                    }
                    Text(
                        "Preview: ${currency?.getSymbol(Locale.getDefault()).orEmpty()} ${normalizedCurrency.ifBlank { "—" }} • ${normalizedTimezone.ifBlank { "—" }}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            item {
                SectionHeading("Receipt settings")
                AppCard {
                    OutlinedTextField(
                        value = taxId,
                        onValueChange = { taxId = it; changed() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tax / GST registration number") },
                        supportingText = { Text("Printed on payment receipts when provided") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = receiptFooter,
                        onValueChange = { receiptFooter = it.take(160); changed() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Receipt footer") },
                        supportingText = { Text("${receiptFooter.length}/160 characters") },
                        minLines = 2
                    )
                }
            }
            saveError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
