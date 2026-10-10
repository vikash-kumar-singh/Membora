package com.membora.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.membora.app.data.MemboraRepository
import com.membora.app.data.DataSyncStatus
import com.membora.app.data.UploadCompression
import com.membora.app.data.asMoney
import com.membora.app.data.pretty
import com.membora.app.domain.MemberStatus
import com.membora.app.firebase.FirebaseServices
import com.membora.app.ui.components.*
import com.membora.app.ui.theme.BrandBlue
import com.membora.app.ui.theme.BrandCyan
import com.membora.app.ui.theme.BrandGreen
import com.membora.app.ui.theme.BrandLime
import com.membora.app.ui.theme.Ink
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(
    repository: MemboraRepository,
    onOpenMembers: () -> Unit,
    onAddMember: () -> Unit,
    onCheckIn: () -> Unit,
    onOpenMember: (String) -> Unit,
    onReports: () -> Unit
) {
    var showNotificationStatus by rememberSaveable { mutableStateOf(false) }
    var logoUploading by rememberSaveable { mutableStateOf(false) }
    var logoMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showOrganizationLogo by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uploadCroppedLogo: (android.net.Uri) -> Unit = { uri ->
        logoUploading = true
        logoMessage = "Preparing cropped logo…"
        scope.launch {
            val prepared = withContext(Dispatchers.IO) {
                UploadCompression.preparePhoto(context.contentResolver, uri)
            }
            prepared.fold(
                onSuccess = { upload ->
                    logoMessage = "Uploading logo…"
                    repository.uploadOrganizationLogo(upload.bytes, upload.contentType) { result ->
                        logoUploading = false
                        logoMessage = result.fold(
                            onSuccess = { "Organization logo updated" },
                            onFailure = { it.localizedMessage ?: "Unable to upload logo" }
                        )
                    }
                },
                onFailure = {
                    logoUploading = false
                    logoMessage = it.localizedMessage ?: "Unable to prepare logo"
                }
            )
        }
    }
    val cropLogo = rememberSquareImageCropper(
        title = "Crop organization logo",
        shape = ImageCropShape.ROUNDED_SQUARE,
        onCropped = uploadCroppedLogo,
        onError = { logoMessage = it }
    )
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(cropLogo)
    }
    if (showOrganizationLogo && repository.organization.logoUrl.isNotBlank()) {
        ZoomableImageDialog(
            title = repository.organization.name,
            subtitle = "Organization logo",
            imageModel = repository.organization.logoUrl,
            contentScale = ContentScale.Fit,
            imageActionLabel = if (logoUploading) "Uploading…" else "Change organization logo",
            imageActionEnabled = !logoUploading,
            onImageAction = {
                showOrganizationLogo = false
                logoPicker.launch("image/*")
            },
            onDismiss = { showOrganizationLogo = false }
        )
    }
    val today = repository.currentDate()
    val active = repository.members.count { repository.memberStatus(it.id) in listOf(MemberStatus.ACTIVE, MemberStatus.EXPIRING_SOON) }
    val expiring = repository.members.filter { repository.memberStatus(it.id) == MemberStatus.EXPIRING_SOON }
    val todayRevenue = repository.payments.filter { it.paidOn.toLocalDate() == today }.sumOf { it.amount }
    val todayVisits = repository.visits.count { it.checkedInAt.toLocalDate() == today }

    if (showNotificationStatus) {
        AlertDialog(
            onDismissRequest = { showNotificationStatus = false },
            title = { Text("Notifications") },
            text = {
                Text(
                    if (FirebaseServices.state.messagingRegistrationAvailable) {
                        "This device is registered for Membora updates. New messages will appear as Android notifications."
                    } else {
                        "Notification registration is still connecting. Check your network and Firebase setup if it does not become available."
                    }
                )
            },
            confirmButton = { TextButton(onClick = { showNotificationStatus = false }) { Text("Done") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(PagePadding),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BrandLockup(Modifier.weight(1f))
                IconButton(onClick = { showNotificationStatus = true }) {
                    Icon(Icons.Rounded.NotificationsNone, contentDescription = "Notifications", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(4.dp))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))) {
                    Text("AM", modifier = Modifier.padding(11.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(18.dp))
            OrganizationBrandHeader(
                repository = repository,
                logoUploading = logoUploading,
                logoMessage = logoMessage,
                onChooseLogo = { logoPicker.launch("image/*") },
                onViewLogo = { showOrganizationLogo = true },
                onDismissMessage = { logoMessage = null }
            )
        }
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(
                    Brush.linearGradient(listOf(Ink, Color(0xFF073B70), BrandBlue))
                )
            ) {
                Box(Modifier.align(Alignment.TopEnd).size(150.dp).offset(x = 45.dp, y = (-45).dp).clip(CircleShape).background(BrandCyan.copy(alpha = 0.13f)))
                Box(Modifier.align(Alignment.BottomEnd).size(105.dp).offset(x = 30.dp, y = 35.dp).clip(CircleShape).background(BrandLime.copy(alpha = 0.14f)))
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Surface(color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(99.dp)) {
                        Text("OPERATIONS OVERVIEW", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Membership operations,\nunder control.", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("$active active members • $todayVisits visits today", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(20.dp))
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth < 350.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                DashboardPrimaryAction(onAddMember, Modifier.fillMaxWidth())
                                DashboardCheckInAction(onCheckIn, Modifier.fillMaxWidth())
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DashboardPrimaryAction(onAddMember)
                                DashboardCheckInAction(onCheckIn)
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionHeading("Today", "View reports", onReports)
            Spacer(Modifier.height(10.dp))
            DashboardMetrics(
                active = active,
                memberCount = repository.members.size,
                expiring = expiring.size,
                revenue = "${repository.organization.currencySymbol}${todayRevenue.asMoney()}",
                visits = todayVisits
            )
        }
        item {
            SectionHeading("Quick actions")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(Icons.Rounded.Add, "Add member", BrandBlue, onAddMember, Modifier.weight(1f))
                QuickAction(Icons.Rounded.QrCodeScanner, "Check in", BrandGreen, onCheckIn, Modifier.weight(1f))
                QuickAction(Icons.Rounded.BarChart, "Reports", BrandCyan, onReports, Modifier.weight(1f))
            }
        }
        item { SectionHeading("Needs attention", "All members", onOpenMembers) }
        if (expiring.isEmpty()) {
            item { AppCard { Text("No memberships expire in the next seven days.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        } else {
            items(expiring) { member ->
                val membership = repository.membershipFor(member.id)
                val plan = membership?.let { repository.plan(it.planId) }
                AppCard(onClick = { onOpenMember(member.id) }) {
                    MemberRow(member, repository.memberStatus(member.id), "${plan?.name ?: "No plan"} • ends ${membership?.endDate?.toUiDate() ?: "—"}", onClick = { onOpenMember(member.id) })
                }
            }
        }
        item { SectionHeading("Recent activity") }
        item {
            AppCard {
                repository.activities.take(4).forEachIndexed { index, event -> TimelineRow(event, index == repository.activities.take(4).lastIndex) }
            }
        }
        item { Spacer(Modifier.height(6.dp)) }
    }
}

@Composable
private fun OrganizationBrandHeader(
    repository: MemboraRepository,
    logoUploading: Boolean,
    logoMessage: String?,
    onChooseLogo: () -> Unit,
    onViewLogo: () -> Unit,
    onDismissMessage: () -> Unit
) {
    val organization = repository.organization
    val sync = repository.syncDetails
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            )
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).size(130.dp)
                .offset(x = 42.dp, y = (-52).dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
        )
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier
                        .size(92.dp)
                        .then(
                            if (!logoUploading) {
                                Modifier.clickable(
                                    onClickLabel = if (organization.logoUrl.isBlank()) "Upload organization logo" else "View organization logo",
                                    onClick = if (organization.logoUrl.isBlank()) onChooseLogo else onViewLogo
                                )
                            } else Modifier
                        ),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                    ),
                    shadowElevation = 2.dp
                ) {
                    if (organization.logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = organization.logoUrl,
                            contentDescription = "${organization.name} logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                organization.name.take(2).uppercase().ifBlank { "OR" },
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "ORGANIZATION",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = if (sync.status == DataSyncStatus.SYNCED) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                sync.status.pretty(),
                                Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        organization.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOf(organization.businessType, organization.branchName)
                            .filter(String::isNotBlank).joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (organization.logoUrl.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap logo to view",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (organization.logoUrl.isBlank()) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onChooseLogo,
                    enabled = !logoUploading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(if (logoUploading) "Uploading…" else "Upload organization logo")
                }
            }
            if (logoMessage != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        logoMessage,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = onDismissMessage) { Text("Dismiss") }
                }
            } else {
                Text(
                    "Shown across your workspace",
                    modifier = Modifier.padding(start = 4.dp, top = 5.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DashboardPrimaryAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink),
        shape = RoundedCornerShape(13.dp)
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("Add member")
    }
}

@Composable
private fun DashboardCheckInAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(13.dp)
    ) {
        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Check in")
    }
}

@Composable
private fun DashboardMetrics(active: Int, memberCount: Int, expiring: Int, revenue: String, visits: Int) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        when {
            maxWidth >= 840.dp -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Active", active.toString(), "of $memberCount members", BrandGreen, Modifier.weight(1f))
                MetricCard("Expiring", expiring.toString(), "in the next 7 days", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                MetricCard("Revenue", revenue, "received today", BrandBlue, Modifier.weight(1f))
                MetricCard("Visits", visits.toString(), "check-ins today", BrandCyan, Modifier.weight(1f))
            }
            maxWidth >= 360.dp -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard("Active", active.toString(), "of $memberCount members", BrandGreen, Modifier.weight(1f))
                    MetricCard("Expiring", expiring.toString(), "in the next 7 days", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard("Revenue", revenue, "received today", BrandBlue, Modifier.weight(1f))
                    MetricCard("Visits", visits.toString(), "check-ins today", BrandCyan, Modifier.weight(1f))
                }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Active", active.toString(), "of $memberCount members", BrandGreen, Modifier.fillMaxWidth())
                MetricCard("Expiring", expiring.toString(), "in the next 7 days", MaterialTheme.colorScheme.secondary, Modifier.fillMaxWidth())
                MetricCard("Revenue", revenue, "received today", BrandBlue, Modifier.fillMaxWidth())
                MetricCard("Visits", visits.toString(), "check-ins today", BrandCyan, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = 0.11f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
