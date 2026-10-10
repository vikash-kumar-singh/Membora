package com.membora.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.membora.app.BuildConfig
import com.membora.app.data.MemboraRepository
import com.membora.app.data.MemboraViewModel
import com.membora.app.data.WorkspaceProvisioningState
import com.membora.app.firebase.FirebaseServices
import com.membora.app.ui.components.BrandMark
import com.membora.app.ui.screens.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class RootTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    MEMBERS("Members", Icons.Rounded.Groups),
    CHECK_IN("Check-in", Icons.Rounded.QrCodeScanner),
    PAYMENTS("Payments", Icons.Rounded.Payments),
    MORE("More", Icons.Rounded.GridView)
}

sealed interface Destination {
    data class Root(val tab: RootTab) : Destination
    data class MemberDetail(val memberId: String, val origin: RootTab = RootTab.MEMBERS) : Destination
    data object AddMember : Destination
    data object Plans : Destination
    data object Services : Destination
    data object Reports : Destination
    data object Automations : Destination
    data object Staff : Destination
    data object OrganizationSettings : Destination
}

private val DestinationSaver = Saver<Destination, String>(
    save = { destination ->
        when (destination) {
            is Destination.Root -> "root:${destination.tab.name}"
            is Destination.MemberDetail -> "member:${destination.origin.name}:${destination.memberId}"
            Destination.AddMember -> "add-member"
            Destination.Plans -> "plans"
            Destination.Services -> "services"
            Destination.Reports -> "reports"
            Destination.Automations -> "automations"
            Destination.Staff -> "staff"
            Destination.OrganizationSettings -> "organization-settings"
        }
    },
    restore = { saved ->
        when {
            saved.startsWith("root:") -> RootTab.entries
                .firstOrNull { it.name == saved.substringAfter(':') }
                ?.let(Destination::Root)
                ?: Destination.Root(RootTab.HOME)
            saved.startsWith("member:") -> {
                val payload = saved.removePrefix("member:")
                val separator = payload.indexOf(':')
                if (separator < 0) {
                    Destination.MemberDetail(payload)
                } else {
                    val origin = RootTab.entries.firstOrNull { it.name == payload.substring(0, separator) }
                        ?: RootTab.MEMBERS
                    Destination.MemberDetail(payload.substring(separator + 1), origin)
                }
            }
            saved == "add-member" -> Destination.AddMember
            saved == "plans" -> Destination.Plans
            saved == "services" -> Destination.Services
            saved == "reports" -> Destination.Reports
            saved == "automations" -> Destination.Automations
            saved == "staff" -> Destination.Staff
            saved == "organization-settings" -> Destination.OrganizationSettings
            else -> Destination.Root(RootTab.HOME)
        }
    }
)

internal fun shouldUseNavigationRail(width: Dp, height: Dp): Boolean =
    width >= 600.dp && height >= 480.dp

internal fun Destination.backDestination(): Destination = when (this) {
    is Destination.MemberDetail -> Destination.Root(origin)
    Destination.AddMember -> Destination.Root(RootTab.MEMBERS)
    Destination.Plans, Destination.Services, Destination.Reports,
    Destination.Automations, Destination.Staff, Destination.OrganizationSettings -> Destination.Root(RootTab.MORE)
    is Destination.Root -> Destination.Root(RootTab.HOME)
}

internal enum class AuthDestination { SPLASH, AUTHENTICATION, WORKSPACE }

internal fun authDestination(
    firebaseConfigured: Boolean,
    splashFinished: Boolean,
    authObserved: Boolean,
    hasUser: Boolean,
    isAnonymous: Boolean,
    guestSessionAccepted: Boolean
): AuthDestination = when {
    !splashFinished || (firebaseConfigured && !authObserved) -> AuthDestination.SPLASH
    !firebaseConfigured -> AuthDestination.WORKSPACE
    !hasUser -> AuthDestination.AUTHENTICATION
    isAnonymous && !guestSessionAccepted -> AuthDestination.AUTHENTICATION
    else -> AuthDestination.WORKSPACE
}

@Composable
fun MemboraApp() {
    val appViewModel: MemboraViewModel = viewModel()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val firebaseAuth = FirebaseServices.auth
    var firebaseUser by remember(firebaseAuth) { mutableStateOf(firebaseAuth?.currentUser) }
    val sessionPreferences = remember(context) {
        context.getSharedPreferences("membora_auth_session", android.content.Context.MODE_PRIVATE)
    }
    var guestSessionAccepted by rememberSaveable {
        mutableStateOf(sessionPreferences.getBoolean("guest_session_accepted", false))
    }
    var authObserved by remember(firebaseAuth) { mutableStateOf(firebaseAuth == null) }
    var splashFinished by rememberSaveable { mutableStateOf(false) }
    var signingOut by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(650)
        splashFinished = true
    }

    DisposableEffect(firebaseAuth) {
        if (firebaseAuth == null) return@DisposableEffect onDispose { }
        val listener = FirebaseAuth.AuthStateListener {
            firebaseUser = it.currentUser
            authObserved = true
        }
        firebaseAuth.addAuthStateListener(listener)
        onDispose { firebaseAuth.removeAuthStateListener(listener) }
    }

    if (signingOut) {
        MemboraSplashScreen("Signing out securely")
        return
    }

    when (authDestination(
        firebaseConfigured = BuildConfig.FIREBASE_CONFIGURED,
        splashFinished = splashFinished,
        authObserved = authObserved,
        hasUser = firebaseUser != null,
        isAnonymous = firebaseUser?.isAnonymous == true,
        guestSessionAccepted = guestSessionAccepted
    )) {
        AuthDestination.SPLASH -> {
            MemboraSplashScreen()
            return
        }
        AuthDestination.AUTHENTICATION -> {
            AuthenticationScreen(
                onContinueAsGuest = {
                    guestSessionAccepted = true
                    sessionPreferences.edit { putBoolean("guest_session_accepted", true) }
                    firebaseUser = FirebaseServices.auth?.currentUser
                },
                onAuthenticated = {
                    guestSessionAccepted = false
                    sessionPreferences.edit { remove("guest_session_accepted") }
                    firebaseUser = FirebaseServices.auth?.currentUser
                }
            )
            return
        }
        AuthDestination.WORKSPACE -> Unit
    }

    NotificationPermissionEffect()
    MemboraApp(
        repository = appViewModel.repository,
        onSignOut = {
            signingOut = true
            guestSessionAccepted = false
            sessionPreferences.edit { remove("guest_session_accepted") }
            FirebaseServices.signOut {
                firebaseUser = null
                signingOut = false
            }
            coroutineScope.launch {
                runCatching {
                    CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                }.onFailure(FirebaseServices::recordException)
            }
        }
    )
}

@Composable
private fun NotificationPermissionEffect() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        FirebaseServices.logEvent(if (granted) "notification_permission_granted" else "notification_permission_denied")
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun MemboraApp(repository: MemboraRepository, onSignOut: () -> Unit = {}) {
    when (repository.workspaceProvisioningState) {
        WorkspaceProvisioningState.LOADING -> {
            MemboraSplashScreen("Loading your organization")
            return
        }
        WorkspaceProvisioningState.NEEDS_ORGANIZATION -> {
            OrganizationSettingsScreen(repository = repository, onBack = onSignOut, setupMode = true)
            return
        }
        WorkspaceProvisioningState.ERROR -> {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Workspace unavailable", style = MaterialTheme.typography.headlineSmall)
                    Text(repository.workspaceProvisioningError ?: "Unable to load this organization", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = repository::retryFirebaseConnection) { Text("Retry connection") }
                    OutlinedButton(onClick = onSignOut) { Text("Return to sign in") }
                }
            }
            return
        }
        WorkspaceProvisioningState.LOCAL, WorkspaceProvisioningState.READY -> Unit
    }
    var destination by rememberSaveable(stateSaver = DestinationSaver) {
        mutableStateOf<Destination>(Destination.Root(RootTab.HOME))
    }

    LaunchedEffect(repository) { repository.checkServerConnection() }

    fun open(next: Destination) { destination = next }
    fun openTab(tab: RootTab) { destination = Destination.Root(tab) }
    fun goBack() {
        destination = destination.backDestination()
    }

    if (destination !is Destination.Root || (destination as? Destination.Root)?.tab != RootTab.HOME) {
        BackHandler { goBack() }
    }

    val root = destination as? Destination.Root
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useNavigationRail = root != null && shouldUseNavigationRail(maxWidth, maxHeight)
        Row(Modifier.fillMaxSize()) {
            if (useNavigationRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = {
                        BrandMark(Modifier.padding(vertical = 12.dp), small = true)
                    }
                ) {
                    Spacer(Modifier.weight(1f))
                    RootTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = root?.tab == tab,
                            onClick = { openTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                            alwaysShowLabel = true,
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                bottomBar = {
                    if (root != null && !useNavigationRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            RootTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = root.tab == tab,
                                    onClick = { openTab(tab) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    if (root?.tab == RootTab.MEMBERS) {
                        ExtendedFloatingActionButton(
                            onClick = { open(Destination.AddMember) },
                            text = { Text("Add member") },
                            icon = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
                            shape = RoundedCornerShape(17.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(if (root != null) innerPadding else PaddingValues()),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(Modifier.fillMaxHeight().widthIn(max = 1100.dp).fillMaxWidth()) {
                        when (val screen = destination) {
                            is Destination.Root -> when (screen.tab) {
                                RootTab.HOME -> DashboardScreen(repository, onOpenMembers = { openTab(RootTab.MEMBERS) }, onAddMember = { open(Destination.AddMember) }, onCheckIn = { openTab(RootTab.CHECK_IN) }, onOpenMember = { open(Destination.MemberDetail(it, RootTab.HOME)) }, onReports = { open(Destination.Reports) })
                                RootTab.MEMBERS -> MembersScreen(repository, onOpenMember = { open(Destination.MemberDetail(it, RootTab.MEMBERS)) })
                                RootTab.CHECK_IN -> CheckInScreen(repository, onOpenMember = { open(Destination.MemberDetail(it, RootTab.CHECK_IN)) })
                                RootTab.PAYMENTS -> PaymentsScreen(repository, onOpenMember = { open(Destination.MemberDetail(it, RootTab.PAYMENTS)) })
                                RootTab.MORE -> MoreScreen(repository, onPlans = { open(Destination.Plans) }, onServices = { open(Destination.Services) }, onReports = { open(Destination.Reports) }, onAutomations = { open(Destination.Automations) }, onStaff = { open(Destination.Staff) }, onSettings = { open(Destination.OrganizationSettings) }, onSignOut = onSignOut)
                            }
                            is Destination.MemberDetail -> MemberDetailScreen(repository, screen.memberId, onBack = ::goBack)
                            Destination.AddMember -> AddMemberScreen(repository, onBack = ::goBack, onCreated = { open(Destination.MemberDetail(it, RootTab.MEMBERS)) })
                            Destination.Plans -> PlansScreen(repository, onBack = ::goBack)
                            Destination.Services -> ServicesScreen(repository, onBack = ::goBack)
                            Destination.Reports -> ReportsScreen(repository, onBack = ::goBack)
                            Destination.Automations -> AutomationsScreen(repository, onBack = ::goBack)
                            Destination.Staff -> StaffScreen(repository, onBack = ::goBack)
                            Destination.OrganizationSettings -> OrganizationSettingsScreen(repository, onBack = ::goBack)
                        }
                    }
                }
            }
        }
    }
}
