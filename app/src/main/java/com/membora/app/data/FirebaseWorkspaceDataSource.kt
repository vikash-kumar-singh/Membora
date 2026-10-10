package com.membora.app.data

import android.os.Handler
import android.os.Looper
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.membora.app.domain.ActivityEvent
import com.membora.app.domain.ActivityType
import com.membora.app.domain.Commitment
import com.membora.app.domain.Entitlement
import com.membora.app.domain.Member
import com.membora.app.domain.MemberDocument
import com.membora.app.domain.MemberDocumentType
import com.membora.app.domain.AutomationRule
import com.membora.app.domain.Communication
import com.membora.app.domain.CommunicationChannel
import com.membora.app.domain.Membership
import com.membora.app.domain.MembershipPlan
import com.membora.app.domain.MembershipStatus
import com.membora.app.domain.Organization
import com.membora.app.domain.Payment
import com.membora.app.domain.PaymentMethod
import com.membora.app.domain.PaymentStatus
import com.membora.app.domain.ServiceOffering
import com.membora.app.domain.StaffMember
import com.membora.app.domain.Visit
import com.membora.app.firebase.FirebaseServices
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * User-owned Firebase workspace used by the Android client.
 *
 * Data lives below users/{uid}/businesses/{businessId}, so a newly authenticated user can use the
 * app without privileged custom claims while the rules still isolate every account.
 */
internal class FirebaseWorkspaceDataSource(
    private val userId: String,
    private val businessId: String,
    private val onOrganization: (Organization) -> Unit,
    private val onOrganizationMissing: () -> Unit,
    private val onWorkspaceError: (Throwable) -> Unit,
    private val onMembers: (List<Member>) -> Unit,
    private val onPlans: (List<MembershipPlan>) -> Unit,
    private val onMemberships: (List<Membership>) -> Unit,
    private val onPayments: (List<Payment>) -> Unit,
    private val onStaff: (List<StaffMember>) -> Unit,
    private val onCommunications: (List<Communication>) -> Unit,
    private val onAutomationRules: (List<AutomationRule>) -> Unit,
    private val onServices: (List<ServiceOffering>) -> Unit,
    private val onVisits: (List<Visit>) -> Unit,
    private val onEntitlements: (List<Entitlement>) -> Unit,
    private val onCommitments: (List<Commitment>) -> Unit,
    private val onActivities: (List<ActivityEvent>) -> Unit,
    private val onSyncDetails: (DataSyncDetails) -> Unit
) {
    companion object {
        private const val MANUAL_SYNC_TIMEOUT_MS = 15_000L
        private const val PENDING_WRITE_WARNING_SECONDS = 12L
        private const val EXPECTED_LISTENERS = 13
    }

    private val database = FirebaseServices.firestore
    private val business = database
        ?.collection("users")
        ?.document(userId)
        ?.collection("businesses")
        ?.document(businessId)
    private val registrations = mutableListOf<ListenerRegistration>()
    private val listenerMetadata = mutableMapOf<String, Pair<Boolean, Boolean>>()
    private var pendingWriteCount = 0
    private var lastServerSyncAt: LocalDateTime? = null
    private var lastAttemptAt: LocalDateTime? = LocalDateTime.now()
    private var lastError: Throwable? = null
    private var manualSyncInProgress = false
    private var oldestPendingWriteAt: LocalDateTime? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start() {
        val businessReference = business ?: run {
            reportError(IllegalStateException("Firestore is not configured"))
            return
        }
        // Organization creation is explicit: never provision a demo workspace for a new account.
        businessReference.get()
            .addOnSuccessListener { snapshot ->
                reportSnapshot("organization-check", snapshot.metadata.isFromCache, snapshot.metadata.hasPendingWrites())
                if (!snapshot.exists() && !snapshot.metadata.isFromCache) onOrganizationMissing()
            }
            .addOnFailureListener { error ->
                FirebaseServices.recordException(error)
                reportError(error)
                onWorkspaceError(error)
            }

        registrations += businessReference.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                FirebaseServices.recordException(error)
                reportError(error)
                onWorkspaceError(error)
                return@addSnapshotListener
            }
            snapshot?.metadata?.let { reportSnapshot("organization", it.isFromCache, it.hasPendingWrites()) }
            snapshot?.takeIf(DocumentSnapshot::exists)?.toOrganization()?.let(onOrganization)
        }
        registrations += businessReference.collection("members").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) return@addSnapshotListener reportError(error)
            snapshot?.metadata?.let { reportSnapshot("members", it.isFromCache, it.hasPendingWrites()) }
            snapshot?.documents?.mapNotNull(DocumentSnapshot::toMember)?.let(onMembers)
        }
        registrations += businessReference.collection("plans").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) return@addSnapshotListener reportError(error)
            snapshot?.metadata?.let { reportSnapshot("plans", it.isFromCache, it.hasPendingWrites()) }
            snapshot?.documents?.mapNotNull(DocumentSnapshot::toPlan)?.let(onPlans)
        }
        registrations += businessReference.collection("memberships").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) return@addSnapshotListener reportError(error)
            snapshot?.metadata?.let { reportSnapshot("memberships", it.isFromCache, it.hasPendingWrites()) }
            snapshot?.documents?.mapNotNull(DocumentSnapshot::toMembership)?.let(onMemberships)
        }
        registrations += businessReference.collection("payments").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) return@addSnapshotListener reportError(error)
            snapshot?.metadata?.let { reportSnapshot("payments", it.isFromCache, it.hasPendingWrites()) }
            snapshot?.documents?.mapNotNull(DocumentSnapshot::toPayment)?.let(onPayments)
        }
        registrations += businessReference.collection("staff").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) return@addSnapshotListener reportError(error)
            snapshot?.metadata?.let { reportSnapshot("staff", it.isFromCache, it.hasPendingWrites()) }
            snapshot?.documents?.mapNotNull(DocumentSnapshot::toStaffMember)?.let(onStaff)
        }
        registrations += businessReference.collection("communications").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) return@addSnapshotListener reportError(error)
            snapshot?.metadata?.let { reportSnapshot("communications", it.isFromCache, it.hasPendingWrites()) }
            snapshot?.documents?.mapNotNull(DocumentSnapshot::toCommunication)?.let(onCommunications)
        }
        registrations += businessReference.collection("automationRules").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) return@addSnapshotListener reportError(error)
            snapshot?.metadata?.let { reportSnapshot("automationRules", it.isFromCache, it.hasPendingWrites()) }
            snapshot?.documents?.mapNotNull(DocumentSnapshot::toAutomationRule)?.let(onAutomationRules)
        }
        registrations += businessReference.collection("services").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) return@addSnapshotListener reportError(error)
            snapshot?.metadata?.let { reportSnapshot("services", it.isFromCache, it.hasPendingWrites()) }
            snapshot?.documents?.mapNotNull(DocumentSnapshot::toService)?.let(onServices)
        }
        registrations += businessReference.collection("visits").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) return@addSnapshotListener reportError(error)
            snapshot?.metadata?.let { reportSnapshot("visits", it.isFromCache, it.hasPendingWrites()) }
            snapshot?.documents?.mapNotNull(DocumentSnapshot::toVisit)?.let(onVisits)
        }
        registrations += businessReference.collection("entitlements").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) return@addSnapshotListener reportError(error)
            snapshot?.metadata?.let { reportSnapshot("entitlements", it.isFromCache, it.hasPendingWrites()) }
            snapshot?.documents?.mapNotNull(DocumentSnapshot::toEntitlement)?.let(onEntitlements)
        }
        registrations += businessReference.collection("commitments").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) return@addSnapshotListener reportError(error)
            snapshot?.metadata?.let { reportSnapshot("commitments", it.isFromCache, it.hasPendingWrites()) }
            snapshot?.documents?.mapNotNull(DocumentSnapshot::toCommitment)?.let(onCommitments)
        }
        registrations += businessReference.collection("activities").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) return@addSnapshotListener reportError(error)
            snapshot?.metadata?.let { reportSnapshot("activities", it.isFromCache, it.hasPendingWrites()) }
            snapshot?.documents?.mapNotNull(DocumentSnapshot::toActivity)?.let(onActivities)
        }
    }

    fun close() {
        registrations.forEach(ListenerRegistration::remove)
        registrations.clear()
        listenerMetadata.clear()
        mainHandler.removeCallbacksAndMessages(null)
    }

    fun syncNow(onResult: (Result<Unit>) -> Unit) {
        val firestore = database
            ?: return onResult(Result.failure(IllegalStateException("Firestore is not configured")))
        val reference = business
            ?: return onResult(Result.failure(IllegalStateException("Workspace is not connected")))
        if (manualSyncInProgress) {
            onResult(Result.failure(IllegalStateException("A server sync is already in progress")))
            return
        }
        lastAttemptAt = LocalDateTime.now()
        lastError = null
        manualSyncInProgress = true
        emitSyncDetails()

        val completed = AtomicBoolean(false)
        lateinit var timeout: Runnable
        fun complete(result: Result<Unit>) {
            if (!completed.compareAndSet(false, true)) return
            mainHandler.removeCallbacks(timeout)
            manualSyncInProgress = false
            result.fold(
                onSuccess = {
                    lastServerSyncAt = LocalDateTime.now()
                    lastError = null
                    emitSyncDetails()
                },
                onFailure = ::reportError
            )
            onResult(result)
        }
        timeout = Runnable {
            complete(Result.failure(IllegalStateException(
                "The server did not confirm sync within 15 seconds. Your changes are safely queued on this device and will retry automatically."
            )))
        }
        mainHandler.postDelayed(timeout, MANUAL_SYNC_TIMEOUT_MS)

        firestore.enableNetwork()
            .addOnSuccessListener {
                // Confirm that this account can reach and read its workspace before waiting for
                // queued writes. Permission and connectivity failures are therefore reported fast.
                reference.get(Source.SERVER)
                    .addOnSuccessListener {
                        lastServerSyncAt = LocalDateTime.now()
                        firestore.waitForPendingWrites()
                            .addOnSuccessListener { complete(Result.success(Unit)) }
                            .addOnFailureListener { complete(Result.failure(it)) }
                    }
                    .addOnFailureListener { complete(Result.failure(it)) }
            }
            .addOnFailureListener { complete(Result.failure(it)) }
    }

    fun saveOrganization(value: Organization, onResult: (Result<Unit>) -> Unit = {}) =
        saveBusinessDocument(value.toFirebaseMap(userId), onResult)
    fun saveMember(value: Member, onResult: (Result<Unit>) -> Unit = {}) =
        saveCollectionDocument("members", value.id, value.toFirebaseMap(), onResult)
    fun savePlan(value: MembershipPlan) = saveCollectionDocument("plans", value.id, value.toFirebaseMap())
    fun saveMembership(value: Membership) = saveCollectionDocument("memberships", value.id, value.toFirebaseMap())
    fun savePayment(value: Payment) = saveCollectionDocument("payments", value.id, value.toFirebaseMap())
    fun saveStaff(value: StaffMember) = saveCollectionDocument("staff", value.id, value.toFirebaseMap())
    fun saveCommunication(value: Communication) = saveCollectionDocument("communications", value.id, value.toFirebaseMap())
    fun saveAutomationRule(value: AutomationRule) = saveCollectionDocument("automationRules", value.id, value.toFirebaseMap())
    fun saveService(value: ServiceOffering) = saveCollectionDocument("services", value.id, value.toFirebaseMap())
    fun saveVisit(value: Visit) = saveCollectionDocument("visits", value.id, value.toFirebaseMap())
    fun saveEntitlement(value: Entitlement) = saveCollectionDocument("entitlements", value.id, value.toFirebaseMap())
    fun saveCommitment(value: Commitment) = saveCollectionDocument("commitments", value.id, value.toFirebaseMap())
    fun saveActivity(value: ActivityEvent) = saveCollectionDocument("activities", value.id, value.toFirebaseMap())

    private fun saveBusinessDocument(data: Map<String, Any?>, onResult: (Result<Unit>) -> Unit = {}) {
        val reference = business ?: run {
            val error = IllegalStateException("Workspace is not connected")
            reportError(error)
            onResult(Result.failure(error))
            return
        }
        beginWrite()
        reference.set(data + ("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .addOnSuccessListener { finishWrite(); onResult(Result.success(Unit)) }
            .addOnFailureListener { error -> finishWrite(error); onResult(Result.failure(error)) }
    }

    private fun saveCollectionDocument(
        collection: String,
        id: String,
        data: Map<String, Any?>,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        val reference = business?.collection(collection)?.document(id) ?: run {
            val error = IllegalStateException("Workspace is not connected")
            reportError(error)
            onResult(Result.failure(error))
            return
        }
        beginWrite()
        reference.set(data + ("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .addOnSuccessListener { finishWrite(); onResult(Result.success(Unit)) }
            .addOnFailureListener { error -> finishWrite(error); onResult(Result.failure(error)) }
    }

    private fun beginWrite() {
        if (pendingWriteCount == 0) oldestPendingWriteAt = LocalDateTime.now()
        pendingWriteCount++
        lastAttemptAt = LocalDateTime.now()
        lastError = null
        emitSyncDetails()
        schedulePendingWriteWarning()
    }

    private fun finishWrite(error: Throwable? = null) {
        pendingWriteCount = (pendingWriteCount - 1).coerceAtLeast(0)
        if (pendingWriteCount == 0 && listenerMetadata.values.none { it.second }) {
            oldestPendingWriteAt = null
        }
        if (error == null) {
            lastServerSyncAt = LocalDateTime.now()
            lastError = null
        } else {
            FirebaseServices.recordException(error)
            lastError = error
        }
        emitSyncDetails()
    }

    private fun reportSnapshot(name: String, fromCache: Boolean, hasPendingWrites: Boolean) {
        listenerMetadata[name] = fromCache to hasPendingWrites
        if (hasPendingWrites && oldestPendingWriteAt == null) {
            oldestPendingWriteAt = LocalDateTime.now()
            schedulePendingWriteWarning()
        } else if (!hasPendingWrites && pendingWriteCount == 0 && listenerMetadata.values.none { it.second }) {
            oldestPendingWriteAt = null
        }
        if (!fromCache && !hasPendingWrites) {
            lastServerSyncAt = LocalDateTime.now()
            if (pendingWriteCount == 0) lastError = null
        }
        emitSyncDetails()
    }

    private fun schedulePendingWriteWarning() {
        mainHandler.postDelayed({
            if (oldestPendingWriteAt != null) emitSyncDetails()
        }, PENDING_WRITE_WARNING_SECONDS * 1_000L)
    }

    private fun reportError(error: Throwable) {
        FirebaseServices.recordException(error)
        lastError = error
        emitSyncDetails()
    }

    private fun emitSyncDetails() {
        val fromCache = listenerMetadata.values.any { it.first }
        val metadataPending = listenerMetadata.values.any { it.second }
        val pending = maxOf(pendingWriteCount, if (metadataPending) 1 else 0)
        val pendingSeconds = oldestPendingWriteAt?.let { ChronoUnit.SECONDS.between(it, LocalDateTime.now()) } ?: 0
        val pendingIsStalled = pending > 0 && pendingSeconds >= PENDING_WRITE_WARNING_SECONDS
        val activeListenerCount = listenerMetadata.keys.count { it != "organization-check" }
            .coerceAtMost(EXPECTED_LISTENERS)
        val status = when {
            manualSyncInProgress -> DataSyncStatus.SYNCING
            lastError is FirebaseFirestoreException && (lastError as FirebaseFirestoreException).code in setOf(
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
            ) -> DataSyncStatus.OFFLINE
            lastError != null -> DataSyncStatus.ERROR
            pendingIsStalled -> DataSyncStatus.OFFLINE
            pending > 0 -> DataSyncStatus.SYNCING
            activeListenerCount < EXPECTED_LISTENERS -> DataSyncStatus.CONNECTING
            fromCache -> DataSyncStatus.OFFLINE
            else -> DataSyncStatus.SYNCED
        }
        onSyncDetails(
            DataSyncDetails(
                status = status,
                usingCachedData = fromCache || pendingIsStalled,
                pendingWrites = pending,
                manualSyncInProgress = manualSyncInProgress,
                activeListeners = activeListenerCount,
                expectedListeners = EXPECTED_LISTENERS,
                lastServerSyncAt = lastServerSyncAt,
                lastAttemptAt = lastAttemptAt,
                errorMessage = lastError?.toSyncMessage() ?: if (pendingIsStalled) {
                    "Server confirmation is delayed. Changes are saved on this device and will retry automatically."
                } else null
            )
        )
    }
}

private fun Throwable.toSyncMessage(): String = when {
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        "Firestore denied access. Check your deployed Firestore rules and Firebase App Check enforcement."
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAUTHENTICATED ->
        "Your Firebase session expired. Sign out and sign in again."
    this is FirebaseFirestoreException && code in setOf(
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
    ) -> "Firebase is unreachable. Changes are safely queued and will retry automatically."
    else -> localizedMessage ?: "Firebase synchronization failed"
}

private fun Organization.toFirebaseMap(ownerUid: String) = mapOf(
    "ownerUid" to ownerUid,
    "name" to name,
    "businessType" to businessType,
    "memberLabel" to memberLabel,
    "currencySymbol" to currencySymbol,
    "currencyCode" to currencyCode,
    "timezone" to timezone,
    "phone" to phone,
    "email" to email,
    "branchName" to branchName,
    "logoUrl" to logoUrl,
    "addressLine1" to addressLine1,
    "addressLine2" to addressLine2,
    "city" to city,
    "state" to state,
    "postalCode" to postalCode,
    "countryCode" to countryCode
)

private fun StaffMember.toFirebaseMap() = mapOf(
    "name" to name,
    "role" to role,
    "phone" to phone,
    "email" to email,
    "branchName" to branchName,
    "active" to active,
    "invitationStatus" to invitationStatus
)

private fun Communication.toFirebaseMap() = mapOf(
    "memberId" to memberId,
    "channel" to channel.name,
    "title" to title,
    "message" to message,
    "status" to status,
    "sentAt" to sentAt.toString(),
    "membershipId" to membershipId,
    "automationRuleId" to automationRuleId,
    "deliveryKey" to deliveryKey,
    "recipient" to recipient,
    "templateKey" to templateKey,
    "templateParameters" to templateParameters,
    "providerMessageId" to providerMessageId,
    "failureReason" to failureReason
)

private fun AutomationRule.toFirebaseMap() = mapOf(
    "name" to name,
    "event" to event,
    "timing" to timing,
    "channel" to channel.name,
    "template" to template,
    "enabled" to enabled,
    "triggerDaysBeforeExpiry" to triggerDaysBeforeExpiry
)

private fun ServiceOffering.toFirebaseMap() = mapOf(
    "organizationId" to organizationId,
    "name" to name,
    "category" to category,
    "description" to description,
    "unitLabel" to unitLabel,
    "active" to active
)

private fun Visit.toFirebaseMap() = mapOf(
    "organizationId" to organizationId,
    "memberId" to memberId,
    "checkedInAt" to checkedInAt.toString(),
    "checkedOutAt" to checkedOutAt?.toString(),
    "method" to method
)

private fun Entitlement.toFirebaseMap() = mapOf(
    "memberId" to memberId,
    "serviceId" to serviceId,
    "allocatedQuantity" to allocatedQuantity,
    "usedQuantity" to usedQuantity,
    "validUntil" to validUntil.toString()
)

private fun Commitment.toFirebaseMap() = mapOf(
    "memberId" to memberId,
    "title" to title,
    "description" to description,
    "quantity" to quantity,
    "validUntil" to validUntil.toString(),
    "status" to status,
    "assignedBy" to assignedBy
)

private fun ActivityEvent.toFirebaseMap() = mapOf(
    "memberId" to memberId,
    "type" to type.name,
    "title" to title,
    "detail" to detail,
    "occurredAt" to occurredAt.toString()
)

private fun Member.toFirebaseMap() = mapOf(
    "organizationId" to organizationId,
    "branchId" to branchId,
    "code" to code,
    "firstName" to firstName,
    "lastName" to lastName,
    "mobile" to mobile,
    "email" to email,
    "city" to city,
    "gender" to gender,
    "dateOfBirth" to dateOfBirth?.toString(),
    "joinedOn" to joinedOn.toString(),
    "emergencyContact" to emergencyContact,
    "notes" to notes,
    "alternatePhone" to alternatePhone,
    "addressLine" to addressLine,
    "postalCode" to postalCode,
    "emergencyContactName" to emergencyContactName,
    "emergencyContactPhone" to emergencyContactPhone,
    "emergencyContactRelationship" to emergencyContactRelationship,
    "customValues" to customValues,
    "photoUrl" to photoUrl,
    "documentUrls" to documentUrls,
    "documents" to documents.map { document ->
        mapOf(
            "fileId" to document.fileId,
            "url" to document.url,
            "name" to document.name,
            "sizeBytes" to document.sizeBytes,
            "contentType" to document.contentType,
            "type" to document.type.name,
            "uploadedAt" to document.uploadedAt.toString()
        )
    }
)

private fun MembershipPlan.toFirebaseMap() = mapOf(
    "organizationId" to organizationId,
    "name" to name,
    "description" to description,
    "durationValue" to durationValue,
    "durationUnit" to durationUnit,
    "price" to price,
    "registrationFee" to registrationFee,
    "active" to active,
    "serviceIds" to serviceIds
)

private fun Membership.toFirebaseMap() = mapOf(
    "organizationId" to organizationId,
    "memberId" to memberId,
    "planId" to planId,
    "startDate" to startDate.toString(),
    "endDate" to endDate.toString(),
    "status" to status.name,
    "amount" to amount,
    "discount" to discount,
    "finalAmount" to finalAmount,
    "paymentStatus" to paymentStatus.name,
    "autoRenew" to autoRenew,
    "createdBy" to createdBy,
    "frozenOn" to frozenOn?.toString(),
    "freezeUntil" to freezeUntil?.toString(),
    "cancelledOn" to cancelledOn?.toString(),
    "statusReason" to statusReason
)

private fun Payment.toFirebaseMap() = mapOf(
    "organizationId" to organizationId,
    "memberId" to memberId,
    "membershipId" to membershipId,
    "receiptNumber" to receiptNumber,
    "amount" to amount,
    "method" to method.name,
    "reference" to reference,
    "paidOn" to paidOn.toString(),
    "status" to status.name,
    "notes" to notes
)

private fun DocumentSnapshot.toOrganization(): Organization? = runCatching {
    Organization(
        id = id,
        name = requiredString("name"),
        businessType = requiredString("businessType"),
        memberLabel = getString("memberLabel") ?: "Member",
        currencySymbol = getString("currencySymbol") ?: "₹",
        timezone = getString("timezone") ?: "Asia/Kolkata",
        phone = getString("phone").orEmpty(),
        email = getString("email").orEmpty(),
        branchName = getString("branchName") ?: "Main",
        currencyCode = getString("currencyCode") ?: when (getString("currencySymbol")) {
            "$" -> "USD"
            "€" -> "EUR"
            "£" -> "GBP"
            "د.إ" -> "AED"
            else -> "INR"
        },
        logoUrl = getString("logoUrl").orEmpty(),
        addressLine1 = getString("addressLine1").orEmpty(),
        addressLine2 = getString("addressLine2").orEmpty(),
        city = getString("city").orEmpty(),
        state = getString("state").orEmpty(),
        postalCode = getString("postalCode").orEmpty(),
        countryCode = getString("countryCode") ?: "IN"
    )
}.getOrNull()

private fun DocumentSnapshot.toStaffMember(): StaffMember? = runCatching {
    StaffMember(
        id = id,
        name = requiredString("name"),
        role = getString("role") ?: "Viewer",
        phone = getString("phone").orEmpty(),
        active = getBoolean("active") ?: true,
        email = getString("email").orEmpty(),
        branchName = getString("branchName") ?: "Main",
        invitationStatus = getString("invitationStatus") ?: "ACTIVE"
    )
}.getOrNull()

private fun DocumentSnapshot.toCommunication(): Communication? = runCatching {
    Communication(
        id = id,
        memberId = requiredString("memberId"),
        channel = enumValueOf(getString("channel") ?: CommunicationChannel.IN_APP.name),
        title = requiredString("title"),
        message = getString("message").orEmpty(),
        status = getString("status") ?: "QUEUED",
        sentAt = LocalDateTime.parse(requiredString("sentAt")),
        membershipId = getString("membershipId"),
        automationRuleId = getString("automationRuleId"),
        deliveryKey = getString("deliveryKey").orEmpty(),
        recipient = getString("recipient").orEmpty(),
        templateKey = getString("templateKey").orEmpty(),
        templateParameters = (get("templateParameters") as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
        providerMessageId = getString("providerMessageId").orEmpty(),
        failureReason = getString("failureReason").orEmpty()
    )
}.getOrNull()

private fun DocumentSnapshot.toAutomationRule(): AutomationRule? = runCatching {
    AutomationRule(
        id = id,
        name = requiredString("name"),
        event = requiredString("event"),
        timing = requiredString("timing"),
        channel = enumValueOf(getString("channel") ?: CommunicationChannel.PUSH.name),
        template = requiredString("template"),
        enabled = getBoolean("enabled") ?: true,
        triggerDaysBeforeExpiry = getLong("triggerDaysBeforeExpiry")?.toInt()
    )
}.getOrNull()

private fun DocumentSnapshot.toService(): ServiceOffering? = runCatching {
    ServiceOffering(
        id = id,
        organizationId = requiredString("organizationId"),
        name = requiredString("name"),
        category = getString("category") ?: "General",
        description = getString("description").orEmpty(),
        unitLabel = getString("unitLabel") ?: "uses",
        active = getBoolean("active") ?: true
    )
}.getOrNull()

private fun DocumentSnapshot.toVisit(): Visit? = runCatching {
    Visit(
        id = id,
        organizationId = requiredString("organizationId"),
        memberId = requiredString("memberId"),
        checkedInAt = LocalDateTime.parse(requiredString("checkedInAt")),
        checkedOutAt = getString("checkedOutAt")?.takeIf(String::isNotBlank)?.let(LocalDateTime::parse),
        method = getString("method") ?: "MANUAL"
    )
}.getOrNull()

private fun DocumentSnapshot.toEntitlement(): Entitlement? = runCatching {
    Entitlement(
        id = id,
        memberId = requiredString("memberId"),
        serviceId = requiredString("serviceId"),
        allocatedQuantity = (getLong("allocatedQuantity") ?: 0L).toInt(),
        usedQuantity = (getLong("usedQuantity") ?: 0L).toInt(),
        validUntil = LocalDate.parse(requiredString("validUntil"))
    )
}.getOrNull()

private fun DocumentSnapshot.toCommitment(): Commitment? = runCatching {
    Commitment(
        id = id,
        memberId = requiredString("memberId"),
        title = requiredString("title"),
        description = getString("description").orEmpty(),
        quantity = (getLong("quantity") ?: 0L).toInt(),
        validUntil = LocalDate.parse(requiredString("validUntil")),
        status = getString("status") ?: "PENDING",
        assignedBy = getString("assignedBy").orEmpty()
    )
}.getOrNull()

private fun DocumentSnapshot.toActivity(): ActivityEvent? = runCatching {
    ActivityEvent(
        id = id,
        memberId = getString("memberId"),
        type = enumValueOf(getString("type") ?: ActivityType.SYSTEM.name),
        title = requiredString("title"),
        detail = getString("detail").orEmpty(),
        occurredAt = LocalDateTime.parse(requiredString("occurredAt"))
    )
}.getOrNull()

private fun DocumentSnapshot.toMember(): Member? = runCatching {
    @Suppress("UNCHECKED_CAST")
    val customValues = (get("customValues") as? Map<String, Any?>)
        ?.mapValues { it.value?.toString().orEmpty() }
        .orEmpty()
    val documents = (get("documents") as? List<*>)
        ?.mapNotNull { raw ->
            val value = raw as? Map<*, *> ?: return@mapNotNull null
            val url = value["url"]?.toString().orEmpty()
            if (url.isBlank()) return@mapNotNull null
            MemberDocument(
                fileId = value["fileId"]?.toString().orEmpty(),
                url = url,
                name = value["name"]?.toString().orEmpty().ifBlank { "Document" },
                sizeBytes = (value["sizeBytes"] as? Number)?.toLong() ?: 0L,
                contentType = value["contentType"]?.toString().orEmpty(),
                type = runCatching {
                    MemberDocumentType.valueOf(value["type"]?.toString().orEmpty())
                }.getOrDefault(MemberDocumentType.OTHER),
                uploadedAt = runCatching {
                    LocalDateTime.parse(value["uploadedAt"]?.toString().orEmpty())
                }.getOrDefault(LocalDateTime.MIN)
            )
        }
        .orEmpty()
    Member(
        id = id,
        organizationId = requiredString("organizationId"),
        branchId = getString("branchId") ?: "branch-main",
        code = requiredString("code"),
        firstName = requiredString("firstName"),
        lastName = getString("lastName").orEmpty(),
        mobile = getString("mobile").orEmpty(),
        email = getString("email").orEmpty(),
        city = getString("city").orEmpty(),
        gender = getString("gender").orEmpty(),
        dateOfBirth = getString("dateOfBirth")?.takeIf(String::isNotBlank)?.let(LocalDate::parse),
        joinedOn = LocalDate.parse(requiredString("joinedOn")),
        emergencyContact = getString("emergencyContact").orEmpty(),
        notes = getString("notes").orEmpty(),
        alternatePhone = getString("alternatePhone").orEmpty(),
        addressLine = getString("addressLine").orEmpty(),
        postalCode = getString("postalCode").orEmpty(),
        emergencyContactName = getString("emergencyContactName").orEmpty(),
        emergencyContactPhone = getString("emergencyContactPhone").orEmpty(),
        emergencyContactRelationship = getString("emergencyContactRelationship").orEmpty(),
        customValues = customValues,
        photoUrl = getString("photoUrl").orEmpty(),
        documentUrls = (get("documentUrls") as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
        documents = documents
    )
}.getOrNull()

private fun DocumentSnapshot.toPlan(): MembershipPlan? = runCatching {
    MembershipPlan(
        id = id,
        organizationId = requiredString("organizationId"),
        name = requiredString("name"),
        description = getString("description").orEmpty(),
        durationValue = (getLong("durationValue") ?: 1L).toInt(),
        durationUnit = getString("durationUnit") ?: "MONTH",
        price = getDouble("price") ?: 0.0,
        registrationFee = getDouble("registrationFee") ?: 0.0,
        active = getBoolean("active") ?: true,
        serviceIds = (get("serviceIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
    )
}.getOrNull()

private fun DocumentSnapshot.toMembership(): Membership? = runCatching {
    Membership(
        id = id,
        organizationId = requiredString("organizationId"),
        memberId = requiredString("memberId"),
        planId = requiredString("planId"),
        startDate = LocalDate.parse(requiredString("startDate")),
        endDate = LocalDate.parse(requiredString("endDate")),
        status = enumValueOf(getString("status") ?: MembershipStatus.ACTIVE.name),
        amount = getDouble("amount") ?: 0.0,
        discount = getDouble("discount") ?: 0.0,
        finalAmount = getDouble("finalAmount") ?: 0.0,
        paymentStatus = enumValueOf(getString("paymentStatus") ?: PaymentStatus.DUE.name),
        autoRenew = getBoolean("autoRenew") ?: false,
        createdBy = getString("createdBy").orEmpty(),
        frozenOn = getString("frozenOn")?.takeIf(String::isNotBlank)?.let(LocalDate::parse),
        freezeUntil = getString("freezeUntil")?.takeIf(String::isNotBlank)?.let(LocalDate::parse),
        cancelledOn = getString("cancelledOn")?.takeIf(String::isNotBlank)?.let(LocalDate::parse),
        statusReason = getString("statusReason").orEmpty()
    )
}.getOrNull()

private fun DocumentSnapshot.toPayment(): Payment? = runCatching {
    Payment(
        id = id,
        organizationId = requiredString("organizationId"),
        memberId = requiredString("memberId"),
        membershipId = getString("membershipId"),
        receiptNumber = requiredString("receiptNumber"),
        amount = getDouble("amount") ?: 0.0,
        method = enumValueOf(getString("method") ?: PaymentMethod.OTHER.name),
        reference = getString("reference").orEmpty(),
        paidOn = LocalDateTime.parse(requiredString("paidOn")),
        status = enumValueOf(getString("status") ?: PaymentStatus.PAID.name),
        notes = getString("notes").orEmpty()
    )
}.getOrNull()

private fun DocumentSnapshot.requiredString(field: String): String =
    getString(field)?.takeIf(String::isNotBlank) ?: error("Missing $field in $reference")
