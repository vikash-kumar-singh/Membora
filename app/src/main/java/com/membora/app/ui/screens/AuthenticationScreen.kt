package com.membora.app.ui.screens

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.membora.app.firebase.FirebaseServices
import com.membora.app.R
import com.membora.app.ui.components.BrandMark
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

private enum class AuthMode { LOGIN, REGISTER, RESET_PASSWORD }

@Composable
fun MemboraSplashScreen(message: String = "Preparing your workspace") {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark()
            Spacer(Modifier.height(24.dp))
            Text("Membora", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator()
        }
    }
}

@Composable
fun StaffInvitationAcceptanceScreen(
    emailLink: String,
    onAccepted: () -> Unit,
    onCancel: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 2.dp
        ) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BrandMark(small = true)
                Text("Accept staff invitation", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Enter the exact email address that received this link. Membora will authenticate it and open the organization with your assigned role.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Invited email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    enabled = !loading
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                    onClick = {
                        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                            error = "Enter the email address that received the invitation."
                            return@Button
                        }
                        loading = true
                        FirebaseServices.acceptStaffInvitation(email, emailLink) { result ->
                            loading = false
                            result.fold(
                                onSuccess = { onAccepted() },
                                onFailure = { error = it.localizedMessage ?: "Unable to accept this invitation" }
                            )
                        }
                    }
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(if (loading) "Joining…" else "Accept and sign in")
                }
                TextButton(onClick = onCancel, enabled = !loading, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun AuthenticationScreen(
    onContinueAsGuest: () -> Unit,
    onAuthenticated: () -> Unit,
    upgradingGuest: Boolean = false,
    guestTrialExpired: Boolean = false,
    installationGuestTrialStartedAtMillis: Long? = null
) {
    val context = LocalContext.current
    val googleWebClientId = stringResource(R.string.default_web_client_id)
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val coroutineScope = rememberCoroutineScope()
    var mode by rememberSaveable(upgradingGuest) {
        mutableStateOf(if (upgradingGuest) AuthMode.REGISTER else AuthMode.LOGIN)
    }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        val emailAddress = email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(emailAddress).matches()) {
            error = "Enter a valid email address."
            return
        }
        if (mode != AuthMode.RESET_PASSWORD && password.length < 6) {
            error = "Password must contain at least 6 characters."
            return
        }
        loading = true
        error = null
        notice = null
        if (mode == AuthMode.RESET_PASSWORD) {
            FirebaseServices.sendPasswordResetEmail(emailAddress) { result ->
                loading = false
                result.fold(
                    onSuccess = {
                        password = ""
                        mode = AuthMode.LOGIN
                        notice = "Password reset email sent. Check your inbox and spam folder."
                    },
                    onFailure = { error = it.localizedMessage ?: "Unable to send the reset email" }
                )
            }
            return
        }
        val callback: (Result<com.google.firebase.auth.FirebaseUser>) -> Unit = { result ->
            loading = false
            result.fold(
                onSuccess = { onAuthenticated() },
                onFailure = { error = it.localizedMessage ?: "Authentication failed" }
            )
        }
        if (mode == AuthMode.LOGIN) FirebaseServices.signInWithEmail(emailAddress, password, callback)
        else FirebaseServices.createEmailAccount(emailAddress, password, callback)
    }

    fun signInWithGoogle() {
        loading = true
        error = null
        coroutineScope.launch {
            try {
                val googleOption = GetSignInWithGoogleOption.Builder(
                    googleWebClientId
                ).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleOption)
                    .build()
                val credential = credentialManager.getCredential(
                    context = context,
                    request = request
                ).credential
                if (credential !is CustomCredential ||
                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    error = "Google returned an unsupported credential. Please try again."
                    loading = false
                    return@launch
                }

                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                FirebaseServices.signInWithGoogleIdToken(
                    idToken = googleCredential.idToken,
                    preserveAnonymousWorkspace = upgradingGuest
                ) { result ->
                    loading = false
                    result.fold(
                        onSuccess = { onAuthenticated() },
                        onFailure = {
                            error = it.localizedMessage
                                ?: "Google sign-in is not enabled for this Firebase project."
                        }
                    )
                }
            } catch (_: GetCredentialCancellationException) {
                loading = false
            } catch (_: NoCredentialException) {
                loading = false
                error = "No Google account is available. Add an account to this device and try again."
            } catch (exception: GetCredentialException) {
                loading = false
                error = exception.localizedMessage
                    ?: "No Google account is available. Add an account to this device and try again."
            } catch (exception: Exception) {
                loading = false
                FirebaseServices.recordException(exception)
                error = exception.localizedMessage ?: "Google sign-in failed. Please try again."
            }
        }
    }

    fun continueAsGuest() {
        loading = true
        error = null
        notice = null
        FirebaseServices.ensureAnonymousUser { result ->
            loading = false
            result.fold(
                onSuccess = { user ->
                    val trial = com.membora.app.ui.guestTrialStatus(
                        anonymousUserCreatedAtMillis = user.metadata?.creationTimestamp,
                        installationTrialStartedAtMillis = installationGuestTrialStartedAtMillis,
                        nowMillis = System.currentTimeMillis()
                    )
                    if (trial.isExpired) {
                        error = "Your 15-day guest trial has ended. Create an account with email or Google to continue."
                    } else {
                        onContinueAsGuest()
                    }
                },
                onFailure = { error = it.localizedMessage ?: "Unable to start a guest session" }
            )
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        val wide = maxWidth >= 760.dp
        Box(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.widthIn(max = if (wide) 960.dp else 520.dp).fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                if (wide) {
                    Row {
                        AuthIntro(Modifier.weight(0.9f))
                        AuthForm(
                            mode = mode,
                            email = email,
                            password = password,
                            loading = loading,
                            error = error,
                            notice = notice,
                            onModeChange = { mode = it; error = null; notice = null },
                            onEmailChange = { email = it },
                            onPasswordChange = { password = it },
                            onSubmit = ::submit,
                            onGoogleSignIn = ::signInWithGoogle,
                            onContinueAsGuest = ::continueAsGuest,
                            upgradingGuest = upgradingGuest,
                            guestTrialExpired = guestTrialExpired,
                            modifier = Modifier.weight(1.1f)
                        )
                    }
                } else {
                    Column {
                        AuthIntro(Modifier.fillMaxWidth())
                        AuthForm(
                            mode = mode,
                            email = email,
                            password = password,
                            loading = loading,
                            error = error,
                            notice = notice,
                            onModeChange = { mode = it; error = null; notice = null },
                            onEmailChange = { email = it },
                            onPasswordChange = { password = it },
                            onSubmit = ::submit,
                            onGoogleSignIn = ::signInWithGoogle,
                            onContinueAsGuest = ::continueAsGuest,
                            upgradingGuest = upgradingGuest,
                            guestTrialExpired = guestTrialExpired,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthIntro(modifier: Modifier = Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.primaryContainer).padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        BrandMark()
        Spacer(Modifier.height(24.dp))
        Text("Your membership workspace", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Securely manage members, memberships, payments, files, and notifications across every screen size.",
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun AuthForm(
    mode: AuthMode,
    email: String,
    password: String,
    loading: Boolean,
    error: String?,
    notice: String?,
    onModeChange: (AuthMode) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onContinueAsGuest: () -> Unit,
    upgradingGuest: Boolean,
    guestTrialExpired: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val title = if (guestTrialExpired) {
            "Your 15-day guest trial has ended"
        } else if (upgradingGuest) "Protect your guest workspace" else when (mode) {
            AuthMode.LOGIN -> "Welcome back"
            AuthMode.REGISTER -> "Create your account"
            AuthMode.RESET_PASSWORD -> "Reset your password"
        }
        val subtitle = if (guestTrialExpired) {
            "Create an account with email or Google to keep this workspace and continue using Membora."
        } else when (mode) {
            AuthMode.LOGIN -> if (upgradingGuest) {
                "Signing into an existing account opens that account; it does not transfer this guest workspace."
            } else "Sign in to sync your Membora workspace."
            AuthMode.REGISTER -> if (upgradingGuest) {
                "Create credentials for this guest workspace so it stays recoverable on other devices."
            } else "Register to keep your workspace backed up with Firebase."
            AuthMode.RESET_PASSWORD -> "Enter your registered email and we’ll send you a reset link."
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (mode != AuthMode.RESET_PASSWORD) {
            OutlinedButton(
                onClick = onGoogleSignIn,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("G", fontWeight = FontWeight.Bold)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    if (loading) "Connecting…"
                    else if (upgradingGuest) "Upgrade with Google"
                    else "Continue with Google"
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f))
                Text("OR USE EMAIL", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.weight(1f))
            }
        }
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        if (mode != AuthMode.RESET_PASSWORD) {
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
        }
        if (mode == AuthMode.LOGIN) {
            TextButton(
                onClick = { onModeChange(AuthMode.RESET_PASSWORD) },
                enabled = !loading,
                modifier = Modifier.align(Alignment.End)
            ) { Text("Forgot password?") }
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Button(onClick = onSubmit, enabled = !loading, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text(
                if (loading) "Please wait…" else when (mode) {
                    AuthMode.LOGIN -> "Sign in"
                    AuthMode.REGISTER -> "Register"
                    AuthMode.RESET_PASSWORD -> "Send reset email"
                }
            )
        }
        if (!upgradingGuest) {
            OutlinedButton(
                onClick = {
                    onModeChange(
                        when (mode) {
                            AuthMode.LOGIN -> AuthMode.REGISTER
                            AuthMode.REGISTER, AuthMode.RESET_PASSWORD -> AuthMode.LOGIN
                        }
                    )
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when (mode) {
                        AuthMode.LOGIN -> "Create an account"
                        AuthMode.REGISTER -> "I already have an account"
                        AuthMode.RESET_PASSWORD -> "Back to sign in"
                    }
                )
            }
        }
        if (mode != AuthMode.RESET_PASSWORD && !guestTrialExpired) {
            OutlinedButton(onClick = onContinueAsGuest, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                Text(if (upgradingGuest) "Return to guest workspace" else "Continue as guest")
            }
        }
    }
}
