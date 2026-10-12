package com.membora.app.firebase

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import androidx.core.content.FileProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.membora.app.BuildConfig
import com.membora.app.billing.AccountSubscription
import com.membora.app.billing.AccountSubscriptionState
import com.membora.app.billing.SubscriptionLoadState
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

enum class FirebaseAvailability {
    UNCONFIGURED,
    INITIALIZING,
    READY,
    ERROR
}

data class FirebaseRuntimeState(
    val availability: FirebaseAvailability,
    val authenticatedUserId: String? = null,
    val messagingRegistrationAvailable: Boolean = false,
    val message: String? = null
)

data class AcceptedStaffInvitation(
    val ownerUid: String,
    val businessId: String,
    val role: String,
    val staffId: String
)

/**
 * Single entry point for the Firebase services used by Membora.
 *
 * The Android SDKs remain dormant when google-services.json has not been supplied, which keeps
 * local development and tests usable before a Firebase project is connected.
 */
object FirebaseServices {
    private const val TAG = "MemboraFirebase"
    private const val PREFERENCES = "membora_firebase"
    private const val INSTALLATION_ID_KEY = "messaging_installation_id"

    @Volatile
    var state = FirebaseRuntimeState(FirebaseAvailability.UNCONFIGURED)
        private set

    var auth: FirebaseAuth? = null
        private set
    // FirebaseFirestore is an application-scoped SDK singleton, not an Activity context.
    @SuppressLint("StaticFieldLeak")
    var firestore: FirebaseFirestore? = null
        private set
    var storage: FirebaseStorage? = null
        private set
    var analytics: FirebaseAnalytics? = null
        private set

    private var crashlytics: FirebaseCrashlytics? = null
    private var pendingGoogleLink: PendingGoogleLink? = null
    private var appContext: Context? = null
    private var initialized = false
    private var activeWorkspaceOwnerUid: String? = null

    fun setActiveWorkspace(ownerUid: String) {
        activeWorkspaceOwnerUid = ownerUid
    }

    fun clearActiveWorkspace() {
        activeWorkspaceOwnerUid = null
    }

    private fun storageOwnerUid(): String? = activeWorkspaceOwnerUid ?: auth?.currentUser?.uid

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext

        if (!BuildConfig.FIREBASE_CONFIGURED) {
            state = FirebaseRuntimeState(
                FirebaseAvailability.UNCONFIGURED,
                message = "Add app/google-services.json to activate Firebase"
            )
            Log.i(TAG, state.message.orEmpty())
            return
        }

        state = FirebaseRuntimeState(FirebaseAvailability.INITIALIZING)
        runCatching {
            val app = FirebaseApp.getApps(context).firstOrNull()
                ?: FirebaseApp.initializeApp(context)
                ?: error("Firebase configuration could not be loaded")

            auth = FirebaseAuth.getInstance(app)
            firestore = FirebaseFirestore.getInstance(app).also { database ->
                database.firestoreSettings = FirebaseFirestoreSettings.Builder(database.firestoreSettings)
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(100L * 1024L * 1024L)
                            .build()
                    )
                    .build()
            }
            storage = FirebaseStorage.getInstance(app)
            analytics = FirebaseAnalytics.getInstance(context)
            crashlytics = FirebaseCrashlytics.getInstance().also {
                it.setCustomKey("application", "Membora")
                it.setCustomKey("build_type", BuildConfig.BUILD_TYPE)
            }

            auth?.addAuthStateListener { firebaseAuth ->
                val userId = firebaseAuth.currentUser?.uid
                state = state.copy(authenticatedUserId = userId)
                analytics?.setUserId(userId)
                if (userId != null) syncCachedMessagingInstallationId()
            }

            FirebaseMessaging.getInstance().register().addOnFailureListener { error ->
                recordException(error)
            }

            analytics?.logEvent("membora_integration_ready", Bundle())
            state = FirebaseRuntimeState(
                availability = FirebaseAvailability.READY,
                authenticatedUserId = auth?.currentUser?.uid,
                messagingRegistrationAvailable = cachedMessagingInstallationId() != null
            )
            ensureAnonymousUser()
        }.onFailure { throwable ->
            state = FirebaseRuntimeState(FirebaseAvailability.ERROR, message = throwable.message)
            Log.e(TAG, "Firebase initialization failed", throwable)
            recordException(throwable)
        }
    }

    /** Creates a temporary authenticated identity until a permanent account is linked or signed in. */
    fun ensureAnonymousUser(onResult: (Result<FirebaseUser>) -> Unit = {}) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            onResult(Result.failure(IllegalStateException("Firebase Auth is not configured")))
            return
        }
        firebaseAuth.currentUser?.let { user ->
            if (user.isAnonymous) ensureGuestTrialRecord(user, onResult)
            else onResult(Result.success(user))
            return
        }
        firebaseAuth.signInAnonymously().addOnCompleteListener { task ->
            val user = if (task.isSuccessful) task.result?.user else null
            if (task.isSuccessful && user != null) {
                analytics?.logEvent("anonymous_sign_in", Bundle())
                ensureGuestTrialRecord(user, onResult)
            } else {
                val error = task.exception ?: IllegalStateException("Anonymous sign-in failed")
                Log.e(TAG, "Anonymous sign-in failed", error)
                recordException(error)
                onResult(Result.failure(error))
            }
        }
    }

    private fun ensureGuestTrialRecord(
        user: FirebaseUser,
        onResult: (Result<FirebaseUser>) -> Unit
    ) {
        val database = firestore
            ?: return onResult(Result.failure(IllegalStateException("Firestore is not configured")))
        val userDocument = database.collection("users").document(user.uid)
        database.runTransaction { transaction ->
            val snapshot = transaction.get(userDocument)
            if (!snapshot.contains("guestTrialStartedAt")) {
                transaction.set(
                    userDocument,
                    mapOf("guestTrialStartedAt" to FieldValue.serverTimestamp()),
                    SetOptions.merge()
                )
            }
        }.addOnSuccessListener { onResult(Result.success(user)) }
            .addOnFailureListener { error ->
                recordException(error)
                onResult(Result.failure(error))
            }
    }

    fun observeCurrentAccountSubscription(
        onChanged: (AccountSubscriptionState) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration? {
        val user = auth?.currentUser
        val database = firestore
        if (user == null || database == null) {
            onChanged(AccountSubscriptionState(
                loadState = SubscriptionLoadState.ERROR,
                message = "Firebase subscription status is unavailable"
            ))
            return null
        }
        return database.collection("users").document(user.uid)
            .collection("account").document("subscription")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    recordException(error)
                    onChanged(AccountSubscriptionState(
                        loadState = SubscriptionLoadState.ERROR,
                        message = error.localizedMessage ?: "Unable to load subscription status"
                    ))
                    return@addSnapshotListener
                }
                val subscription = if (snapshot?.exists() == true) {
                    AccountSubscription(
                        entitled = snapshot.getBoolean("entitled") == true,
                        status = snapshot.getString("status").orEmpty().ifBlank { "NONE" },
                        productId = snapshot.getString("productId").orEmpty(),
                        basePlanId = snapshot.getString("basePlanId").orEmpty(),
                        expiresAt = snapshot.getTimestamp("expiresAt")?.toDate()?.toInstant(),
                        autoRenewing = snapshot.getBoolean("autoRenewing") == true,
                        verifiedAt = snapshot.getTimestamp("verifiedAt")?.toDate()?.toInstant()
                    )
                } else AccountSubscription()
                onChanged(AccountSubscriptionState(
                    loadState = SubscriptionLoadState.LOADED,
                    subscription = subscription
                ))
            }
    }

    fun createEmailAccount(email: String, password: String, onResult: (Result<FirebaseUser>) -> Unit) {
        val firebaseAuth = auth ?: return onResult(Result.failure(IllegalStateException("Firebase Auth is not configured")))
        val emailAddress = email.trim()
        val anonymousUser = firebaseAuth.currentUser?.takeIf(FirebaseUser::isAnonymous)
        val accountTask = if (anonymousUser != null) {
            val credential = EmailAuthProvider.getCredential(emailAddress, password)
            anonymousUser.linkWithCredential(credential)
        } else {
            firebaseAuth.createUserWithEmailAndPassword(emailAddress, password)
        }
        accountTask.addOnCompleteListener { task ->
            val user = if (task.isSuccessful) task.result?.user else null
            if (task.isSuccessful && user != null) {
                analytics?.logEvent("account_registered", Bundle())
                onResult(Result.success(user))
            }
            else onResult(Result.failure(task.exception ?: IllegalStateException("Account creation failed")))
        }
    }

    fun signInWithEmail(email: String, password: String, onResult: (Result<FirebaseUser>) -> Unit) {
        val firebaseAuth = auth ?: return onResult(Result.failure(IllegalStateException("Firebase Auth is not configured")))
        val emailAddress = email.trim()
        firebaseAuth.signInWithEmailAndPassword(emailAddress, password).addOnCompleteListener { task ->
            val user = if (task.isSuccessful) task.result?.user else null
            if (task.isSuccessful && user != null) {
                analytics?.logEvent("account_signed_in", Bundle())
                linkPendingGoogleCredential(user, emailAddress, onResult)
            }
            else onResult(Result.failure(task.exception ?: IllegalStateException("Sign-in failed")))
        }
    }

    fun sendPasswordResetEmail(email: String, onResult: (Result<Unit>) -> Unit) {
        val firebaseAuth = auth
            ?: return onResult(Result.failure(IllegalStateException("Firebase Auth is not configured")))
        val emailAddress = email.trim()
        if (emailAddress.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Enter your email address")))
            return
        }
        firebaseAuth.sendPasswordResetEmail(emailAddress).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                analytics?.logEvent("password_reset_requested", Bundle())
                onResult(Result.success(Unit))
            } else {
                val error = task.exception ?: IllegalStateException("Unable to send the reset email")
                recordException(error)
                onResult(Result.failure(error))
            }
        }
    }

    fun sendStaffInvitationEmail(
        email: String,
        invitationId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val firebaseAuth = auth
            ?: return onResult(Result.failure(IllegalStateException("Firebase Auth is not configured")))
        val emailAddress = email.trim().lowercase()
        val continueUrl = "https://membora-app.firebaseapp.com/invite?invitation=$invitationId"
        val settings = ActionCodeSettings.newBuilder()
            .setUrl(continueUrl)
            .setHandleCodeInApp(true)
            .setAndroidPackageName(BuildConfig.APPLICATION_ID, true, BuildConfig.VERSION_CODE.toString())
            .build()
        firebaseAuth.sendSignInLinkToEmail(emailAddress, settings).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                analytics?.logEvent("staff_invitation_email_sent", Bundle())
                onResult(Result.success(Unit))
            } else {
                val error = task.exception ?: IllegalStateException("Unable to send the staff invitation email")
                recordException(error)
                onResult(Result.failure(error))
            }
        }
    }

    fun isStaffInvitationLink(link: String?): Boolean =
        !link.isNullOrBlank() && auth?.isSignInWithEmailLink(link) == true &&
            invitationIdFromEmailLink(link) != null

    fun acceptStaffInvitation(
        email: String,
        emailLink: String,
        onResult: (Result<AcceptedStaffInvitation>) -> Unit
    ) {
        val firebaseAuth = auth
            ?: return onResult(Result.failure(IllegalStateException("Firebase Auth is not configured")))
        val database = firestore
            ?: return onResult(Result.failure(IllegalStateException("Firestore is not configured")))
        val emailAddress = email.trim().lowercase()
        if (emailAddress.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Enter the email address that received this invitation")))
            return
        }
        val invitationId = invitationIdFromEmailLink(emailLink)
            ?: return onResult(Result.failure(IllegalArgumentException("This staff invitation link is invalid")))
        if (!firebaseAuth.isSignInWithEmailLink(emailLink)) {
            onResult(Result.failure(IllegalArgumentException("This staff invitation link is invalid or incomplete")))
            return
        }

        firebaseAuth.signInWithEmailLink(emailAddress, emailLink).addOnCompleteListener { signInTask ->
            val user = if (signInTask.isSuccessful) signInTask.result?.user else null
            if (user == null) {
                val error = signInTask.exception ?: IllegalStateException("Unable to authenticate this invitation")
                recordException(error)
                onResult(Result.failure(error))
                return@addOnCompleteListener
            }
            val authenticatedEmail = user.email?.trim()?.lowercase()
            if (authenticatedEmail != emailAddress) {
                firebaseAuth.signOut()
                onResult(Result.failure(IllegalStateException("Use the same email address that received the invitation")))
                return@addOnCompleteListener
            }

            val invitation = database.collection("staffInvitations").document(invitationId)
            database.runTransaction { transaction ->
                val snapshot = transaction.get(invitation)
                check(snapshot.exists()) { "This staff invitation no longer exists" }
                val invitedEmail = snapshot.getString("email")?.trim()?.lowercase().orEmpty()
                check(invitedEmail == authenticatedEmail) { "This invitation belongs to another email address" }
                val status = snapshot.getString("status").orEmpty()
                check(status in setOf("PENDING", "SENT", "DELIVERY_FAILED")) {
                    if (status == "ACCEPTED") "This invitation has already been accepted" else "This invitation is no longer active"
                }
                val expiresAt = snapshot.getTimestamp("expiresAt")
                check(expiresAt == null || expiresAt > Timestamp.now()) { "This invitation has expired. Ask the owner to resend it." }

                val ownerUid = snapshot.getString("ownerUid").orEmpty()
                val businessId = snapshot.getString("businessId").orEmpty()
                val staffId = snapshot.getString("staffId").orEmpty()
                val role = snapshot.getString("role").orEmpty()
                check(ownerUid.isNotBlank() && businessId.isNotBlank() && staffId.isNotBlank() && role.isNotBlank()) {
                    "This invitation is incomplete"
                }
                val accessId = "${ownerUid}_$businessId"
                val access = database.collection("users").document(user.uid)
                    .collection("workspaceAccess").document(accessId)
                val staff = database.collection("users").document(ownerUid)
                    .collection("businesses").document(businessId)
                    .collection("staff").document(staffId)

                transaction.update(invitation, mapOf(
                    "status" to "ACCEPTED",
                    "acceptedUid" to user.uid,
                    "acceptedAt" to FieldValue.serverTimestamp()
                ))
                transaction.set(access, mapOf(
                    "ownerUid" to ownerUid,
                    "businessId" to businessId,
                    "staffId" to staffId,
                    "role" to role,
                    "email" to authenticatedEmail,
                    "status" to "ACTIVE",
                    "invitationId" to invitationId,
                    "acceptedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                transaction.update(staff, mapOf(
                    "active" to true,
                    "invitationStatus" to "ACTIVE",
                    "firebaseUid" to user.uid,
                    "invitationId" to invitationId,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                AcceptedStaffInvitation(ownerUid, businessId, role, staffId)
            }.addOnSuccessListener { accepted ->
                analytics?.logEvent("staff_invitation_accepted", Bundle().apply {
                    putString("role", accepted.role)
                })
                onResult(Result.success(accepted))
            }.addOnFailureListener { error ->
                recordException(error)
                onResult(Result.failure(error))
            }
        }
    }

    private fun invitationIdFromEmailLink(link: String): String? {
        fun invitationFrom(value: String): String? = runCatching {
            Uri.parse(value).getQueryParameter("invitation")?.takeIf(String::isNotBlank)
        }.getOrNull()

        invitationFrom(link)?.let { return it }
        var nested = runCatching { Uri.parse(link).getQueryParameter("continueUrl") }.getOrNull()
        repeat(3) {
            val value = nested ?: return@repeat
            invitationFrom(value)?.let { return it }
            nested = runCatching { Uri.parse(value).getQueryParameter("continueUrl") }.getOrNull()
        }
        return null
    }

    /** Exchanges a Google ID token for a Firebase session without ever handling a Google password. */
    fun signInWithGoogleIdToken(
        idToken: String,
        preserveAnonymousWorkspace: Boolean = false,
        onResult: (Result<FirebaseUser>) -> Unit
    ) {
        val firebaseAuth = auth
            ?: return onResult(Result.failure(IllegalStateException("Firebase Auth is not configured")))
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val anonymousUser = firebaseAuth.currentUser?.takeIf(FirebaseUser::isAnonymous)

        fun signIn() {
            firebaseAuth.signInWithCredential(credential).addOnCompleteListener { task ->
                val user = if (task.isSuccessful) task.result?.user else null
                if (user != null) {
                    pendingGoogleLink = null
                    analytics?.logEvent("google_account_signed_in", Bundle())
                    onResult(Result.success(user))
                } else {
                    val exception = task.exception
                    if (exception is FirebaseAuthUserCollisionException &&
                        exception.errorCode == "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL"
                    ) {
                        pendingGoogleLink = PendingGoogleLink(credential, exception.email)
                        onResult(Result.failure(IllegalStateException(
                            "This Google email already has a Membora password. Sign in with that Membora password once to connect Google."
                        )))
                    } else {
                        onResult(Result.failure(exception ?: IllegalStateException("Google sign-in failed")))
                    }
                }
            }
        }

        if (anonymousUser == null) {
            signIn()
            return
        }

        // Preserve a guest workspace when this Google account is new. If it already belongs to a
        // Firebase user, switch to that existing user instead of surfacing a collision error.
        anonymousUser.linkWithCredential(credential).addOnCompleteListener { task ->
            val user = if (task.isSuccessful) task.result?.user else null
            if (user != null) {
                pendingGoogleLink = null
                analytics?.logEvent("google_account_linked", Bundle())
                onResult(Result.success(user))
            } else if (task.exception is FirebaseAuthUserCollisionException) {
                if (preserveAnonymousWorkspace) {
                    onResult(Result.failure(IllegalStateException(
                        "That Google account already has a Membora workspace. Use a new Google account or register a new email to keep this guest workspace."
                    )))
                } else {
                    signIn()
                }
            } else {
                onResult(Result.failure(task.exception ?: IllegalStateException("Google sign-in failed")))
            }
        }
    }

    private fun linkPendingGoogleCredential(
        user: FirebaseUser,
        emailAddress: String,
        onResult: (Result<FirebaseUser>) -> Unit
    ) {
        val pending = pendingGoogleLink?.takeIf {
            it.email.isNullOrBlank() || it.email.equals(emailAddress, ignoreCase = true)
        }
        if (pending == null) {
            onResult(Result.success(user))
            return
        }

        pendingGoogleLink = null
        user.linkWithCredential(pending.credential).addOnCompleteListener { task ->
            val linkedUser = if (task.isSuccessful) task.result?.user else null
            if (linkedUser != null) {
                analytics?.logEvent("google_account_linked", Bundle())
                onResult(Result.success(linkedUser))
            } else {
                task.exception?.let(::recordException)
                // The email/password session is still valid even if linking was rejected.
                onResult(Result.success(user))
            }
        }
    }

    fun signOut(onResult: (Result<Unit>) -> Unit = {}) {
        val firebaseAuth = auth ?: return onResult(Result.success(Unit))
        val userId = firebaseAuth.currentUser?.uid
        val installationId = cachedMessagingInstallationId()
        val database = firestore
        pendingGoogleLink = null

        val finished = AtomicBoolean(false)
        fun finishSignOut() {
            if (!finished.compareAndSet(false, true)) return
            firebaseAuth.signOut()
            state = state.copy(authenticatedUserId = null)
            onResult(Result.success(Unit))
        }

        if (userId == null || installationId == null || database == null) {
            finishSignOut()
            return
        }

        val deviceId = UUID.nameUUIDFromBytes(installationId.toByteArray()).toString()
        database.collection("users").document(userId).collection("devices").document(deviceId)
            .delete()
            .addOnCompleteListener { task ->
                task.exception?.let(::recordException)
                finishSignOut()
            }
        // Never leave the user trapped in a signing-out state when the device is offline.
        Handler(Looper.getMainLooper()).postDelayed(::finishSignOut, 1_500)
    }

    fun saveCurrentUserData(data: Map<String, Any?>, onResult: (Result<Unit>) -> Unit = {}) {
        val userId = auth?.currentUser?.uid
            ?: return onResult(Result.failure(IllegalStateException("A Firebase user is required")))
        val database = firestore
            ?: return onResult(Result.failure(IllegalStateException("Firestore is not configured")))
        database.collection("users").document(userId).set(data, SetOptions.merge())
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { error -> recordException(error); onResult(Result.failure(error)) }
    }

    fun uploadCurrentUserFile(
        path: String,
        bytes: ByteArray,
        contentType: String? = null,
        contentEncoding: String? = null,
        customMetadata: Map<String, String> = emptyMap(),
        privateFile: Boolean = false,
        onResult: (Result<Uri>) -> Unit
    ) {
        val userId = storageOwnerUid()
            ?: return onResult(Result.failure(IllegalStateException("A Firebase user is required")))
        val firebaseStorage = storage
            ?: return onResult(Result.failure(IllegalStateException("Firebase Storage is not configured")))
        val safePath = path.trim('/').replace("..", "")
        if (safePath.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("A non-empty storage path is required")))
            return
        }
        val metadata = StorageMetadata.Builder().apply {
            if (!contentType.isNullOrBlank()) setContentType(contentType)
            if (!contentEncoding.isNullOrBlank()) setContentEncoding(contentEncoding)
            customMetadata.forEach { (key, value) -> setCustomMetadata(key, value) }
        }.build()
        val reference = firebaseStorage.reference.child("users").child(userId).child(safePath)
        fun complete(uri: Uri) {
            analytics?.logEvent("member_file_uploaded", Bundle().apply {
                putString("content_type", contentType ?: "unknown")
                putString("content_encoding", contentEncoding ?: "identity")
                putLong("size_bytes", bytes.size.toLong())
            })
            onResult(Result.success(uri))
        }
        fun fail(error: Exception) {
            recordException(error)
            onResult(Result.failure(readableStorageError(error)))
        }

        val upload = reference.putBytes(bytes, metadata)
        if (privateFile) {
            // Store an authenticated Storage reference rather than a permanent download-token URL.
            upload.addOnSuccessListener { complete(Uri.parse(reference.toString())) }
                .addOnFailureListener(::fail)
        } else {
            upload.continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("Upload failed")
                reference.downloadUrl
            }.addOnSuccessListener(::complete)
                .addOnFailureListener(::fail)
        }
    }

    fun downloadCurrentUserFile(storageUri: String, onResult: (Result<Uri>) -> Unit) {
        val userId = storageOwnerUid()
            ?: return onResult(Result.failure(IllegalStateException("Sign in before opening documents")))
        val firebaseStorage = storage
            ?: return onResult(Result.failure(IllegalStateException("Firebase Storage is not configured")))
        val context = appContext
            ?: return onResult(Result.failure(IllegalStateException("Application storage is not ready")))
        val reference = runCatching { firebaseStorage.getReferenceFromUrl(storageUri) }
            .getOrElse { return onResult(Result.failure(IllegalArgumentException("Document storage reference is invalid", it))) }
        val expectedPrefix = "users/$userId/"
        if (!reference.path.trimStart('/').startsWith(expectedPrefix)) {
            onResult(Result.failure(IllegalArgumentException("Document is outside this user's workspace")))
            return
        }

        val directory = File(context.cacheDir, "private-documents").apply { mkdirs() }
        val safeName = reference.name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(96)
            .ifBlank { "document" }
        val localFile = File(directory, "${UUID.randomUUID()}-$safeName")
        reference.getFile(localFile)
            .addOnSuccessListener {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    localFile
                )
                onResult(Result.success(uri))
            }
            .addOnFailureListener { error ->
                localFile.delete()
                recordException(error)
                onResult(Result.failure(readableStorageError(error)))
            }
    }

    /** Deletes every direct file in a user-owned folder except the files still referenced by Firestore. */
    fun deleteUnreferencedCurrentUserFiles(
        directoryPath: String,
        keepUrls: Set<String>,
        onResult: (Result<Unit>) -> Unit
    ) {
        val userId = storageOwnerUid()
            ?: return onResult(Result.failure(IllegalStateException("Sign in before cleaning up files")))
        val firebaseStorage = storage
            ?: return onResult(Result.failure(IllegalStateException("Firebase Storage is not configured")))
        val safeDirectory = directoryPath.trim('/').replace("..", "")
        if (safeDirectory.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("A storage directory is required")))
            return
        }
        val ownedPrefix = "users/$userId/"
        val keepPaths = keepUrls.mapNotNull { url ->
            runCatching { firebaseStorage.getReferenceFromUrl(url).path.trimStart('/') }
                .getOrNull()
                ?.takeIf { it.startsWith(ownedPrefix) }
        }.toSet()
        if (keepUrls.isNotEmpty() && keepPaths.isEmpty()) {
            onResult(Result.failure(IllegalArgumentException("The current file reference could not be verified")))
            return
        }
        val directory = firebaseStorage.reference.child("users").child(userId).child(safeDirectory)
        directory.listAll()
            .addOnSuccessListener { listing ->
                val staleFiles = listing.items.filterNot { it.path.trimStart('/') in keepPaths }
                if (staleFiles.isEmpty()) {
                    onResult(Result.success(Unit))
                    return@addOnSuccessListener
                }
                var remaining = staleFiles.size
                var firstError: Exception? = null
                staleFiles.forEach { reference ->
                    reference.delete().addOnCompleteListener { task ->
                        if (!task.isSuccessful && firstError == null) {
                            firstError = task.exception ?: IllegalStateException("Unable to delete an old file")
                        }
                        remaining--
                        if (remaining == 0) {
                            firstError?.let(::recordException)
                            onResult(firstError?.let { Result.failure(it) } ?: Result.success(Unit))
                        }
                    }
                }
            }
            .addOnFailureListener { error ->
                recordException(error)
                onResult(Result.failure(readableStorageError(error)))
            }
    }

    fun deleteCurrentUserFile(fileUrl: String, onResult: (Result<Unit>) -> Unit = {}) {
        val userId = storageOwnerUid()
            ?: return onResult(Result.failure(IllegalStateException("Sign in before deleting files")))
        val firebaseStorage = storage
            ?: return onResult(Result.failure(IllegalStateException("Firebase Storage is not configured")))
        val reference = runCatching { firebaseStorage.getReferenceFromUrl(fileUrl) }
            .getOrElse { return onResult(Result.failure(IllegalArgumentException("File reference is invalid", it))) }
        if (!reference.path.trimStart('/').startsWith("users/$userId/")) {
            onResult(Result.failure(IllegalArgumentException("File is outside this user's workspace")))
            return
        }
        reference.delete()
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { error ->
                recordException(error)
                onResult(Result.failure(readableStorageError(error)))
            }
    }

    private fun readableStorageError(error: Exception): Throwable {
        if (error !is StorageException) return error
        return when {
            error.httpResultCode == 404 || error.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND ->
                IllegalStateException(
                    "Firebase Storage is not ready. Create the default Storage bucket in Firebase Console, then retry.",
                    error
                )
            error.errorCode == StorageException.ERROR_NOT_AUTHENTICATED ->
                IllegalStateException("Sign in again before uploading files.", error)
            error.errorCode == StorageException.ERROR_NOT_AUTHORIZED ->
                IllegalStateException("Upload was blocked by Firebase Storage rules.", error)
            else -> error
        }
    }

    fun logEvent(name: String, parameters: Bundle = Bundle()) {
        analytics?.logEvent(name, parameters)
    }

    fun logFileUploaded(contentType: String?, sizeBytes: Long, provider: String) {
        analytics?.logEvent("member_file_uploaded", Bundle().apply {
            putString("content_type", contentType ?: "unknown")
            putString("storage_provider", provider)
            putLong("size_bytes", sizeBytes)
        })
    }

    fun recordException(throwable: Throwable) {
        crashlytics?.recordException(throwable)
    }

    fun onMessagingRegistered(installationId: String) {
        appContext?.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            ?.edit { putString(INSTALLATION_ID_KEY, installationId) }
        state = state.copy(messagingRegistrationAvailable = true)
        syncMessagingInstallationId(installationId)
    }

    fun onMessagingUnregistered(installationId: String) {
        val preferences = appContext?.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences?.getString(INSTALLATION_ID_KEY, null) == installationId) {
            preferences.edit { remove(INSTALLATION_ID_KEY) }
        }
        state = state.copy(messagingRegistrationAvailable = false)

        val userId = auth?.currentUser?.uid ?: return
        val database = firestore ?: return
        val deviceId = UUID.nameUUIDFromBytes(installationId.toByteArray()).toString()
        database.collection("users").document(userId).collection("devices").document(deviceId)
            .delete()
            .addOnFailureListener(::recordException)
    }

    private fun cachedMessagingInstallationId(): String? = appContext
        ?.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        ?.getString(INSTALLATION_ID_KEY, null)

    private fun syncCachedMessagingInstallationId() {
        cachedMessagingInstallationId()?.let(::syncMessagingInstallationId)
    }

    private fun syncMessagingInstallationId(installationId: String) {
        val userId = auth?.currentUser?.uid ?: return
        val database = firestore ?: return
        val deviceId = UUID.nameUUIDFromBytes(installationId.toByteArray()).toString()
        database.collection("users").document(userId).collection("devices").document(deviceId)
            .set(
                mapOf(
                    "installationId" to installationId,
                    "platform" to "android",
                    "timestamp" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .addOnFailureListener(::recordException)
    }
}

private data class PendingGoogleLink(val credential: AuthCredential, val email: String?)
