package com.membora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.membora.app.domain.*
import com.membora.app.data.remote.ApiClient
import com.membora.app.data.remote.HealthResponse
import com.membora.app.data.remote.ImageKitUploadService
import com.membora.app.core.media.MediaType
import com.membora.app.firebase.FirebaseServices
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale
import java.util.UUID
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

enum class WorkspaceProvisioningState { LOCAL, LOADING, NEEDS_ORGANIZATION, READY, ERROR }

class MemboraRepository {
    private var firebaseDataSource: FirebaseWorkspaceDataSource? = null
    private var firebaseUserId: String? = null

    var serverStatus by mutableStateOf("Connecting")
        private set

    var workspaceProvisioningState by mutableStateOf(WorkspaceProvisioningState.LOCAL)
        private set

    var workspaceProvisioningError by mutableStateOf<String?>(null)
        private set

    var syncDetails by mutableStateOf(DataSyncDetails())
        private set

    var organization by mutableStateOf(
        Organization(
            id = "org-1",
            name = "Northstar Collective",
            businessType = "Fitness & Wellness Club",
            memberLabel = "Member",
            currencySymbol = "₹",
            timezone = "Asia/Kolkata",
            phone = "+91 98765 43210",
            email = "hello@northstar.example",
            branchName = "Indiranagar",
            currencyCode = "INR"
        )
    )
        private set

    val services = mutableStateListOf(
        ServiceOffering("svc-access", "org-1", "Club access", "Access", "Daily access to the facility", "visits"),
        ServiceOffering("svc-coach", "org-1", "Personal coaching", "Coaching", "One-to-one coaching session", "sessions"),
        ServiceOffering("svc-consult", "org-1", "Wellness consultation", "Consultation", "Goal and nutrition consultation", "sessions"),
        ServiceOffering("svc-locker", "org-1", "Reserved locker", "Facility", "Dedicated locker allocation", "months"),
        ServiceOffering("svc-guest", "org-1", "Guest pass", "Access", "Single-day guest entry", "passes")
    )

    val plans = mutableStateListOf(
        MembershipPlan("plan-flex", "org-1", "Flex Monthly", "A flexible monthly membership", 1, "MONTH", 1999.0, serviceIds = listOf("svc-access")),
        MembershipPlan("plan-growth", "org-1", "Growth Quarterly", "Three months with guided support", 3, "MONTH", 4999.0, 250.0, serviceIds = listOf("svc-access", "svc-consult")),
        MembershipPlan("plan-complete", "org-1", "Complete Annual", "Full-year access and premium benefits", 12, "MONTH", 14999.0, serviceIds = listOf("svc-access", "svc-coach", "svc-locker", "svc-guest")),
        MembershipPlan("plan-visits", "org-1", "10 Visit Pack", "Ten entries, valid for 60 days", 10, "VISIT", 1499.0, serviceIds = listOf("svc-access"))
    )

    val members = mutableStateListOf<Member>()
    val memberships = mutableStateListOf<Membership>()
    val payments = mutableStateListOf<Payment>()
    val visits = mutableStateListOf<Visit>()
    val entitlements = mutableStateListOf<Entitlement>()
    val commitments = mutableStateListOf<Commitment>()
    val communications = mutableStateListOf<Communication>()
    val activities = mutableStateListOf<ActivityEvent>()
    val automationRules = mutableStateListOf(
        AutomationRule("rule-7", "7-day expiry reminder", "MEMBERSHIP_EXPIRING", "7 days before", CommunicationChannel.PUSH, "Hi {{member_name}}, your {{membership_name}} expires on {{expiry_date}}. Renew to continue.", true, 7),
        AutomationRule("rule-3", "3-day expiry reminder", "MEMBERSHIP_EXPIRING", "3 days before", CommunicationChannel.PUSH, "Hi {{member_name}}, only 3 days remain on your {{membership_name}} membership.", true, 3),
        AutomationRule("rule-1", "1-day expiry reminder", "MEMBERSHIP_EXPIRING", "1 day before", CommunicationChannel.PUSH, "Hi {{member_name}}, your {{membership_name}} expires tomorrow.", true, 1),
        AutomationRule("rule-expired", "Expired membership notification", "MEMBERSHIP_EXPIRED", "After expiry", CommunicationChannel.PUSH, "Hi {{member_name}}, your {{membership_name}} membership has expired. Renew to restore access.", true, -1),
        AutomationRule("rule-payment-due", "Payment due reminder", "PAYMENT_DUE", "7 days before expiry", CommunicationChannel.WHATSAPP, "Hi {{member_name}}, {{amount_due}} is due for your {{membership_name}} membership. Please pay before {{expiry_date}}. — {{organization_name}}", true, 7),
        AutomationRule("rule-birthday", "Birthday greeting", "MEMBER_BIRTHDAY", "At 09:00", CommunicationChannel.WHATSAPP, "Happy birthday {{member_name}}! — {{organization_name}}", false)
    )
    val staff = mutableStateListOf(
        StaffMember("staff-1", "Aarav Mehta", "Owner", "+91 98765 43210", true, "aarav@northstar.example", "Indiranagar"),
        StaffMember("staff-2", "Diya Rao", "Manager", "+91 98765 43211", true, "diya@northstar.example", "Indiranagar"),
        StaffMember("staff-3", "Kabir Shah", "Front Desk", "+91 98765 43212", true, "kabir@northstar.example", "Indiranagar")
    )

    val businessTypes = listOf(
        "Fitness & Wellness",
        "Education & Academy",
        "Sports Club",
        "Coworking Space",
        "Professional Association",
        "Nonprofit & Community",
        "Other"
    )

    val staffRoles = listOf("Owner", "Administrator", "Manager", "Front Desk", "Accountant", "Viewer")

    private val loadedAutomationSources = mutableSetOf<String>()
    private val requiredAutomationSources = setOf("members", "plans", "memberships", "payments", "communications", "automationRules")
    @Volatile
    var isAutomationDataReady: Boolean = true
        private set
    var lastAutomationCheckAt by mutableStateOf<LocalDateTime?>(null)
        private set
    var lastAutomationCreatedCount by mutableStateOf(0)
        private set
    private var pendingAutomationNotificationCount = 0

    init { seedMembers() }

    fun connectFirebase(userId: String) {
        if (firebaseUserId == userId) return
        disconnectFirebase()
        firebaseUserId = userId
        loadedAutomationSources.clear()
        isAutomationDataReady = false
        workspaceProvisioningState = WorkspaceProvisioningState.LOADING
        workspaceProvisioningError = null
        syncDetails = DataSyncDetails(status = DataSyncStatus.CONNECTING, lastAttemptAt = LocalDateTime.now())
        firebaseDataSource = FirebaseWorkspaceDataSource(
            userId = userId,
            businessId = organization.id,
            onOrganization = {
                organization = it
                workspaceProvisioningState = WorkspaceProvisioningState.READY
                workspaceProvisioningError = null
                processDueReminders()
            },
            onOrganizationMissing = {
                workspaceProvisioningState = WorkspaceProvisioningState.NEEDS_ORGANIZATION
            },
            onWorkspaceError = { error ->
                if (workspaceProvisioningState == WorkspaceProvisioningState.LOADING) {
                    workspaceProvisioningState = WorkspaceProvisioningState.ERROR
                    workspaceProvisioningError = error.localizedMessage ?: "Unable to load this workspace"
                }
            },
            onMembers = { remote -> members.clear(); members.addAll(remote); markAutomationSourceLoaded("members") },
            onPlans = { remote -> plans.clear(); plans.addAll(remote); markAutomationSourceLoaded("plans") },
            onMemberships = { remote -> memberships.clear(); memberships.addAll(remote); markAutomationSourceLoaded("memberships") },
            onPayments = { remote -> payments.clear(); payments.addAll(remote); markAutomationSourceLoaded("payments") },
            onStaff = { remote -> staff.clear(); staff.addAll(remote) },
            onCommunications = { remote -> communications.clear(); communications.addAll(remote); markAutomationSourceLoaded("communications") },
            onAutomationRules = { remote ->
                // New organizations are seeded explicitly during creation. An empty cached
                // snapshot must not repeatedly enqueue the same default rules while offline.
                remote.forEach { value ->
                    val index = automationRules.indexOfFirst { it.id == value.id }
                    if (index >= 0) automationRules[index] = value else automationRules.add(value)
                }
                markAutomationSourceLoaded("automationRules")
            },
            onServices = { remote -> services.clear(); services.addAll(remote) },
            onVisits = { remote -> visits.clear(); visits.addAll(remote.sortedByDescending { it.checkedInAt }) },
            onEntitlements = { remote -> entitlements.clear(); entitlements.addAll(remote) },
            onCommitments = { remote -> commitments.clear(); commitments.addAll(remote) },
            onActivities = { remote -> activities.clear(); activities.addAll(remote.sortedByDescending { it.occurredAt }) },
            onSyncDetails = { syncDetails = it }
        ).also { it.start() }
    }

    fun disconnectFirebase() {
        firebaseDataSource?.close()
        firebaseDataSource = null
        firebaseUserId = null
        loadedAutomationSources.clear()
        isAutomationDataReady = true
        workspaceProvisioningState = WorkspaceProvisioningState.LOCAL
        workspaceProvisioningError = null
        syncDetails = DataSyncDetails(status = DataSyncStatus.LOCAL_ONLY)
    }

    fun retryFirebaseConnection() {
        val userId = firebaseUserId ?: FirebaseServices.auth?.currentUser?.uid ?: return
        disconnectFirebase()
        connectFirebase(userId)
    }

    fun syncNow(onResult: (Result<Unit>) -> Unit = {}) {
        val dataSource = firebaseDataSource
        if (dataSource == null) {
            val error = IllegalStateException("Sign in to Firebase to synchronize data")
            syncDetails = syncDetails.copy(
                status = DataSyncStatus.LOCAL_ONLY,
                lastAttemptAt = LocalDateTime.now(),
                errorMessage = error.message
            )
            onResult(Result.failure(error))
            return
        }
        syncDetails = syncDetails.copy(
            status = DataSyncStatus.SYNCING,
            manualSyncInProgress = true,
            lastAttemptAt = LocalDateTime.now(),
            errorMessage = null
        )
        dataSource.syncNow(onResult)
    }

    fun checkServerConnection() {
        serverStatus = "Connecting"
        ApiClient.api.health().enqueue(object : Callback<HealthResponse> {
            override fun onResponse(call: Call<HealthResponse>, response: Response<HealthResponse>) {
                serverStatus = if (response.isSuccessful && response.body()?.status == "UP") "API connected" else "API unavailable"
            }

            override fun onFailure(call: Call<HealthResponse>, throwable: Throwable) {
                serverStatus = "Offline mode"
            }
        })
    }

    fun member(id: String): Member? = members.firstOrNull { it.id == id }
    fun plan(id: String): MembershipPlan? = plans.firstOrNull { it.id == id }
    fun service(id: String): ServiceOffering? = services.firstOrNull { it.id == id }
    fun membershipFor(memberId: String): Membership? {
        val today = currentDate()
        val memberMemberships = memberships.filter { it.memberId == memberId }
        val current = memberMemberships
            .filter {
                it.status != MembershipStatus.CANCELLED &&
                    !today.isBefore(it.startDate) && !today.isAfter(it.endDate)
            }
            .maxByOrNull { it.endDate }
        if (current != null) return current
        val upcoming = memberMemberships
            .filter { it.status != MembershipStatus.CANCELLED && it.startDate.isAfter(today) }
            .minByOrNull { it.startDate }
        return upcoming ?: memberMemberships.maxWithOrNull(
            compareBy<Membership> { it.startDate }.thenBy { it.endDate }
        )
    }

    fun membershipsFor(memberId: String): List<Membership> = memberships
        .filter { it.memberId == memberId }
        .sortedWith(compareByDescending<Membership> { it.startDate }.thenByDescending { it.endDate })

    fun paymentsFor(memberId: String) = payments.filter { it.memberId == memberId }.sortedByDescending { it.paidOn }
    fun visitsFor(memberId: String) = visits.filter { it.memberId == memberId }.sortedByDescending { it.checkedInAt }
    fun activitiesFor(memberId: String) = activities.filter { it.memberId == memberId }.sortedByDescending { it.occurredAt }
    fun communicationsFor(memberId: String) = communications.filter { it.memberId == memberId }.sortedByDescending { it.sentAt }

    fun currentDate(): LocalDate = LocalDate.now(organizationZone())

    fun currentDateTime(): LocalDateTime = LocalDateTime.now(organizationZone())

    private fun canonicalPhone(value: String): String {
        val digits = value.filter(Char::isDigit)
        return if (
            organization.countryCode.equals("IN", ignoreCase = true) &&
            digits.length == 12 && digits.startsWith("91")
        ) digits.drop(2) else digits
    }

    private fun canonicalEmail(value: String): String = value.trim().lowercase(Locale.ROOT)

    fun effectiveStatus(membership: Membership?, today: LocalDate = currentDate()): MembershipStatus {
        if (membership == null) return MembershipStatus.EXPIRED
        if (membership.status in listOf(MembershipStatus.SUSPENDED, MembershipStatus.CANCELLED, MembershipStatus.FROZEN)) return membership.status
        return when {
            today.isBefore(membership.startDate) -> MembershipStatus.UPCOMING
            today.isAfter(membership.endDate) -> MembershipStatus.EXPIRED
            ChronoUnit.DAYS.between(today, membership.endDate) <= 7 -> MembershipStatus.EXPIRING_SOON
            else -> MembershipStatus.ACTIVE
        }
    }

    fun memberStatus(memberId: String): MemberStatus = when (effectiveStatus(membershipFor(memberId))) {
        MembershipStatus.ACTIVE -> MemberStatus.ACTIVE
        MembershipStatus.EXPIRING_SOON -> MemberStatus.EXPIRING_SOON
        MembershipStatus.UPCOMING -> MemberStatus.UPCOMING
        MembershipStatus.SUSPENDED, MembershipStatus.FROZEN -> MemberStatus.SUSPENDED
        else -> MemberStatus.EXPIRED
    }

    @Synchronized
    fun addMember(request: NewMemberRequest): String {
        val normalizedMobile = canonicalPhone(request.mobile)
        val normalizedEmail = canonicalEmail(request.email)
        require(request.firstName.isNotBlank()) { "First name is required" }
        require(normalizedMobile.length >= 8) { "Enter a valid mobile number" }
        require(normalizedEmail.isBlank() || normalizedEmail.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) {
            "Enter a valid email address"
        }
        require(members.none { canonicalPhone(it.mobile) == normalizedMobile }) {
            "A member with this mobile number already exists"
        }
        require(normalizedEmail.isBlank() || members.none { canonicalEmail(it.email) == normalizedEmail }) {
            "A member with this email address already exists"
        }
        require(request.dateOfBirth == null || !request.dateOfBirth.isAfter(currentDate())) {
            "Date of birth cannot be in the future"
        }
        require(request.emergencyContactPhone.isBlank() || request.emergencyContactPhone.count(Char::isDigit) >= 8) {
            "Enter a valid emergency phone number"
        }
        require(request.customValues.size <= 20) { "A member can have up to 20 custom fields" }
        request.planId?.let { planId ->
            val selectedPlan = requireNotNull(plan(planId)) { "Membership plan not found" }
            require(selectedPlan.active) { "This membership plan is paused" }
            val start = request.membershipStartDate ?: currentDate()
            val end = request.membershipEndDate ?: requireNotNull(calculateMembershipEndDate(planId, start))
            require(!end.isBefore(start)) { "Expiry date must be on or after the start date" }
            require(request.paymentAmount in 0.0..(selectedPlan.price + selectedPlan.registrationFee)) {
                "Payment cannot exceed the membership value"
            }
        }
        val now = currentDateTime()
        // A stable identity makes concurrent registration attempts converge on the same
        // Firestore document even when they originate from different devices.
        val memberId = UUID.nameUUIDFromBytes(
            "${organization.id}:member:$normalizedMobile".toByteArray()
        ).toString()
        val code = "MEM-${(members.size + 1001).toString().padStart(6, '0')}"
        val member = Member(
            id = memberId,
            organizationId = organization.id,
            branchId = "branch-main",
            code = code,
            firstName = request.firstName.trim(),
            lastName = request.lastName.trim(),
            mobile = request.mobile.trim(),
            email = normalizedEmail,
            city = request.city.trim(),
            gender = request.gender.trim(),
            dateOfBirth = request.dateOfBirth,
            joinedOn = now.toLocalDate(),
            emergencyContact = request.emergencyContact.trim().ifBlank {
                listOf(request.emergencyContactName, request.emergencyContactPhone)
                    .map(String::trim).filter(String::isNotBlank).joinToString(" • ")
            },
            notes = request.notes.trim(),
            alternatePhone = request.alternatePhone.trim(),
            addressLine = request.addressLine.trim(),
            postalCode = request.postalCode.trim(),
            emergencyContactName = request.emergencyContactName.trim(),
            emergencyContactPhone = request.emergencyContactPhone.trim(),
            emergencyContactRelationship = request.emergencyContactRelationship.trim(),
            customValues = request.customValues
                .mapKeys { it.key.trim() }
                .mapValues { it.value.trim() }
                .filter { it.key.isNotBlank() && it.value.isNotBlank() }
        )
        members.add(0, member)
        firebaseDataSource?.saveMember(member)
        addActivity(memberId, ActivityType.MEMBER, "Member registered", "$code created at ${organization.branchName}")
        request.planId?.let { planId ->
            val membershipId = activateMembership(
                memberId = memberId,
                planId = planId,
                paymentAmount = request.paymentAmount,
                startDate = request.membershipStartDate ?: currentDate(),
                endDate = request.membershipEndDate
            )
            if (request.paymentAmount > 0) recordPayment(memberId, membershipId, request.paymentAmount, request.paymentMethod, "Onboarding payment")
        }
        return memberId
    }

    fun updateMemberProfile(
        memberId: String,
        firstName: String,
        lastName: String,
        mobile: String,
        email: String,
        alternatePhone: String,
        gender: String,
        dateOfBirth: LocalDate?,
        addressLine: String,
        city: String,
        postalCode: String,
        notes: String,
        emergencyContactName: String,
        emergencyContactPhone: String,
        emergencyContactRelationship: String,
        customValues: Map<String, String>
    ): Result<Unit> = runCatching {
        val index = members.indexOfFirst { it.id == memberId }
        require(index >= 0) { "Member not found" }
        val normalizedEmail = email.trim()
        val normalizedMobile = canonicalPhone(mobile)
        val normalizedCustomValues = customValues
            .mapKeys { it.key.trim() }
            .mapValues { it.value.trim() }
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
        require(firstName.isNotBlank()) { "First name is required" }
        require(normalizedMobile.length >= 8) { "Enter a valid mobile number" }
        require(normalizedEmail.isBlank() || normalizedEmail.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) {
            "Enter a valid email address"
        }
        require(dateOfBirth == null || !dateOfBirth.isAfter(currentDate())) { "Date of birth cannot be in the future" }
        require(emergencyContactPhone.isBlank() || emergencyContactPhone.count(Char::isDigit) >= 8) {
            "Enter a valid emergency phone number"
        }
        require(normalizedCustomValues.size <= 20) { "A member can have up to 20 custom fields" }
        require(members.none { it.id != memberId && canonicalPhone(it.mobile) == normalizedMobile }) {
            "Another member already uses this mobile number"
        }
        val canonicalUpdatedEmail = canonicalEmail(normalizedEmail)
        require(canonicalUpdatedEmail.isBlank() || members.none {
            it.id != memberId && canonicalEmail(it.email) == canonicalUpdatedEmail
        }) { "Another member already uses this email address" }
        val current = members[index]
        val emergencySummary = listOf(emergencyContactName, emergencyContactPhone)
            .map(String::trim).filter(String::isNotBlank).joinToString(" • ")
        val updated = current.copy(
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            mobile = mobile.trim(),
            email = canonicalUpdatedEmail,
            alternatePhone = alternatePhone.trim(),
            gender = gender.trim(),
            dateOfBirth = dateOfBirth,
            addressLine = addressLine.trim(),
            city = city.trim(),
            postalCode = postalCode.trim(),
            notes = notes.trim(),
            emergencyContact = emergencySummary,
            emergencyContactName = emergencyContactName.trim(),
            emergencyContactPhone = emergencyContactPhone.trim(),
            emergencyContactRelationship = emergencyContactRelationship.trim(),
            customValues = normalizedCustomValues
        )
        members[index] = updated
        firebaseDataSource?.saveMember(updated)
        addActivity(memberId, ActivityType.MEMBER, "Member profile updated", "Personal, contact and emergency information saved")
    }

    fun calculateMembershipEndDate(planId: String, startDate: LocalDate): LocalDate? = plan(planId)?.let { plan ->
        when (plan.durationUnit.uppercase(Locale.ROOT)) {
            "DAY" -> startDate.plusDays(plan.durationValue.toLong()).minusDays(1)
            "WEEK" -> startDate.plusWeeks(plan.durationValue.toLong()).minusDays(1)
            "YEAR" -> startDate.plusYears(plan.durationValue.toLong()).minusDays(1)
            "VISIT" -> startDate.plusDays(60)
            else -> startDate.plusMonths(plan.durationValue.toLong()).minusDays(1)
        }
    }

    fun outstandingBalance(membershipId: String): Double {
        val membership = memberships.firstOrNull { it.id == membershipId } ?: return 0.0
        val paid = payments.filter { it.membershipId == membershipId && it.status == PaymentStatus.PAID }.sumOf(Payment::amount)
        return (membership.finalAmount - paid).coerceAtLeast(0.0)
    }

    @Synchronized
    fun activateMembership(
        memberId: String,
        planId: String,
        paymentAmount: Double = 0.0,
        startDate: LocalDate = currentDate(),
        endDate: LocalDate? = null
    ): String {
        require(member(memberId) != null) { "Member not found" }
        val plan = requireNotNull(plan(planId)) { "Membership plan not found" }
        require(plan.active) { "This membership plan is paused" }
        val start = startDate
        val end = endDate ?: requireNotNull(calculateMembershipEndDate(planId, start))
        require(!end.isBefore(start)) { "Expiry date must be on or after the start date" }
        require(memberships.none {
            it.memberId == memberId && it.planId == planId && it.startDate == start &&
                it.endDate == end && it.status != MembershipStatus.CANCELLED
        }) { "This membership term already exists for the member" }
        val membershipId = UUID.nameUUIDFromBytes(
            "${organization.id}:membership:$memberId:$planId:$start:$end".toByteArray()
        ).toString()
        val finalAmount = plan.price + plan.registrationFee
        require(paymentAmount >= 0.0 && paymentAmount <= finalAmount) { "Payment cannot exceed the membership value" }
        val paymentStatus = if (paymentAmount >= finalAmount) PaymentStatus.PAID else if (paymentAmount > 0) PaymentStatus.PARTIAL else PaymentStatus.DUE
        val initialStatus = if (start.isAfter(currentDate())) MembershipStatus.UPCOMING else MembershipStatus.ACTIVE
        val membership = Membership(membershipId, organization.id, memberId, planId, start, end, initialStatus, plan.price, 0.0, finalAmount, paymentStatus, createdBy = "Aarav Mehta")
        memberships.add(membership)
        firebaseDataSource?.saveMembership(membership)
        grantPlanEntitlements(memberId, plan, end)
        addActivity(memberId, ActivityType.MEMBERSHIP, "Membership activated", "${plan.name} • valid until $end")
        return membershipId
    }

    @Synchronized
    fun renewMembership(
        memberId: String,
        planId: String,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        paymentAmount: Double = 0.0,
        paymentMethod: PaymentMethod = PaymentMethod.UPI
    ): String {
        require(member(memberId) != null) { "Member not found" }
        val plan = requireNotNull(plan(planId)) { "Membership plan not found" }
        require(plan.active) { "This membership plan is paused" }
        val current = membershipFor(memberId)
        val today = currentDate()
        val suggestedStart = if (current != null && current.status != MembershipStatus.CANCELLED && !current.endDate.isBefore(today)) current.endDate.plusDays(1) else today
        val start = startDate ?: suggestedStart
        val end = endDate ?: requireNotNull(calculateMembershipEndDate(planId, start))
        require(!end.isBefore(start)) { "Expiry date must be on or after the start date" }
        require(memberships.none {
            it.memberId == memberId && it.planId == planId && it.startDate == start &&
                it.endDate == end && it.status != MembershipStatus.CANCELLED
        }) { "This renewal term already exists for the member" }
        val finalAmount = plan.price + plan.registrationFee
        require(paymentAmount >= 0.0 && paymentAmount <= finalAmount) { "Payment cannot exceed the membership value" }
        val id = UUID.nameUUIDFromBytes(
            "${organization.id}:membership:$memberId:$planId:$start:$end".toByteArray()
        ).toString()
        val status = if (start.isAfter(today)) MembershipStatus.UPCOMING else MembershipStatus.ACTIVE
        val paymentStatus = if (paymentAmount >= finalAmount) PaymentStatus.PAID else if (paymentAmount > 0) PaymentStatus.PARTIAL else PaymentStatus.DUE
        val membership = Membership(id, organization.id, memberId, planId, start, end, status, plan.price, 0.0, finalAmount, paymentStatus, createdBy = "Aarav Mehta")
        memberships.add(membership)
        firebaseDataSource?.saveMembership(membership)
        grantPlanEntitlements(memberId, plan, end)
        if (paymentAmount > 0) recordPayment(memberId, id, paymentAmount, paymentMethod, "Renewal payment")
        addActivity(memberId, ActivityType.MEMBERSHIP, "Membership renewed", "${plan.name} • $start to $end")
        return id
    }

    fun freezeMembership(membershipId: String, freezeUntil: LocalDate?, reason: String): Result<Unit> = runCatching {
        val index = memberships.indexOfFirst { it.id == membershipId }
        require(index >= 0) { "Membership not found" }
        val current = memberships[index]
        require(effectiveStatus(current) in listOf(MembershipStatus.ACTIVE, MembershipStatus.EXPIRING_SOON)) {
            "Only an active membership can be frozen"
        }
        require(freezeUntil == null || !freezeUntil.isBefore(currentDate())) { "Freeze end date cannot be in the past" }
        val updated = current.copy(
            status = MembershipStatus.FROZEN,
            frozenOn = currentDate(),
            freezeUntil = freezeUntil,
            statusReason = reason.trim()
        )
        memberships[index] = updated
        firebaseDataSource?.saveMembership(updated)
        addActivity(current.memberId, ActivityType.MEMBERSHIP, "Membership frozen", listOfNotNull(reason.trim().takeIf(String::isNotBlank), freezeUntil?.let { "until $it" }).joinToString(" • ").ifBlank { "Paused by staff" })
    }

    fun resumeMembership(membershipId: String): Result<Unit> = runCatching {
        val index = memberships.indexOfFirst { it.id == membershipId }
        require(index >= 0) { "Membership not found" }
        val current = memberships[index]
        require(current.status == MembershipStatus.FROZEN) { "This membership is not frozen" }
        val frozenDays = current.frozenOn?.let { ChronoUnit.DAYS.between(it, currentDate()).coerceAtLeast(0) } ?: 0
        val updated = current.copy(
            endDate = current.endDate.plusDays(frozenDays),
            status = MembershipStatus.ACTIVE,
            frozenOn = null,
            freezeUntil = null,
            statusReason = ""
        )
        memberships[index] = updated
        firebaseDataSource?.saveMembership(updated)
        addActivity(current.memberId, ActivityType.MEMBERSHIP, "Membership resumed", if (frozenDays > 0) "Expiry extended by $frozenDays days" else "Access restored")
    }

    fun cancelMembership(membershipId: String, reason: String): Result<Unit> = runCatching {
        val index = memberships.indexOfFirst { it.id == membershipId }
        require(index >= 0) { "Membership not found" }
        val current = memberships[index]
        require(current.status != MembershipStatus.CANCELLED) { "Membership is already cancelled" }
        val updated = current.copy(
            status = MembershipStatus.CANCELLED,
            cancelledOn = currentDate(),
            frozenOn = null,
            freezeUntil = null,
            statusReason = reason.trim()
        )
        memberships[index] = updated
        firebaseDataSource?.saveMembership(updated)
        addActivity(current.memberId, ActivityType.MEMBERSHIP, "Membership cancelled", reason.trim().ifBlank { "Cancelled by staff" })
    }

    @Synchronized
    fun recordPayment(memberId: String, membershipId: String?, amount: Double, method: PaymentMethod, notes: String = ""): String {
        require(member(memberId) != null) { "Member not found" }
        require(amount > 0.0) { "Payment amount must be greater than zero" }
        val membershipIndex = membershipId?.let { id -> memberships.indexOfFirst { it.id == id && it.memberId == memberId } }
        if (membershipId != null) {
            require(membershipIndex != null && membershipIndex >= 0) { "Membership not found for this member" }
            require(memberships[membershipIndex].status != MembershipStatus.CANCELLED) { "Payments cannot be added to a cancelled membership" }
            require(amount <= outstandingBalance(membershipId)) { "Payment exceeds the outstanding balance" }
        }
        val now = currentDateTime()
        require(payments.none {
            it.memberId == memberId && it.membershipId == membershipId &&
                kotlin.math.abs(it.amount - amount) < 0.001 && it.method == method &&
                it.notes.trim() == notes.trim() &&
                ChronoUnit.SECONDS.between(it.paidOn, now) in 0..5
        }) { "This payment was already recorded" }
        val id = UUID.randomUUID().toString()
        val receipt = "RCPT-${currentDate().year}-${(payments.size + 1).toString().padStart(5, '0')}"
        val payment = Payment(id, organization.id, memberId, membershipId, receipt, amount, method, "", now, PaymentStatus.PAID, notes.trim())
        payments.add(0, payment)
        firebaseDataSource?.savePayment(payment)
        if (membershipIndex != null && membershipIndex >= 0) {
            val current = memberships[membershipIndex]
            val outstanding = outstandingBalance(current.id)
            val updated = current.copy(paymentStatus = if (outstanding <= 0.009) PaymentStatus.PAID else PaymentStatus.PARTIAL)
            memberships[membershipIndex] = updated
            firebaseDataSource?.saveMembership(updated)
        }
        addActivity(memberId, ActivityType.PAYMENT, "Payment received", "${organization.currencySymbol}${amount.asMoney()} via ${method.pretty()}")
        return id
    }

    fun welcomeMessageDraft(memberId: String): MemberMessageDraft {
        val member = requireNotNull(member(memberId)) { "Member not found" }
        val membership = membershipFor(memberId)
        val plan = membership?.let { plan(it.planId) }
        val latestPayment = paymentsFor(memberId).firstOrNull()
        val details = buildList {
            add("Hi ${member.firstName},")
            add("")
            add("Welcome to ${organization.name}! Your member ID is ${member.code}.")
            if (membership != null && plan != null) {
                add("Membership: ${plan.name}")
                add("Valid: ${membership.startDate} to ${membership.endDate}")
                latestPayment?.let { add("Payment received: ${organization.currencySymbol}${it.amount.asMoney()} (${it.receiptNumber})") }
                val due = outstandingBalance(membership.id)
                if (due > 0.009) add("Balance due: ${organization.currencySymbol}${due.asMoney()}")
            }
            add("")
            add("Thank you for joining us.")
            add("— ${organization.name}")
        }
        return MemberMessageDraft(member.id, member.mobile, "Welcome new member", details.joinToString("\n"), membership?.id)
    }

    fun paymentConfirmationDraft(paymentId: String): MemberMessageDraft {
        val payment = requireNotNull(payments.firstOrNull { it.id == paymentId }) { "Payment not found" }
        val member = requireNotNull(member(payment.memberId)) { "Member not found" }
        val remaining = payment.membershipId?.let(::outstandingBalance) ?: 0.0
        val message = buildList {
            add("Hi ${member.firstName},")
            add("")
            add("We received your payment of ${organization.currencySymbol}${payment.amount.asMoney()} via ${payment.method.pretty()}.")
            add("Receipt: ${payment.receiptNumber}")
            if (remaining > 0.009) add("Remaining balance: ${organization.currencySymbol}${remaining.asMoney()}")
            else add("Your membership payment is fully paid. Thank you!")
            add("")
            add("— ${organization.name}")
        }.joinToString("\n")
        return MemberMessageDraft(member.id, member.mobile, "Payment confirmation", message, payment.membershipId)
    }

    fun dueReminderDraft(memberId: String): MemberMessageDraft {
        val member = requireNotNull(member(memberId)) { "Member not found" }
        val membership = requireNotNull(membershipFor(memberId)) { "No membership found" }
        val plan = requireNotNull(plan(membership.planId)) { "Membership plan not found" }
        val due = outstandingBalance(membership.id)
        require(due > 0.009) { "This member has no outstanding payment" }
        val message = """Hi ${member.firstName},

This is a friendly reminder that ${organization.currencySymbol}${due.asMoney()} is due for your ${plan.name} membership.
Membership validity: ${membership.startDate} to ${membership.endDate}.

Please complete the payment at your convenience. If already paid, please ignore this message.
— ${organization.name}"""
        return MemberMessageDraft(member.id, member.mobile, "Payment due reminder", message, membership.id)
    }

    fun renewalThankYouDraft(memberId: String, membershipId: String): MemberMessageDraft {
        val member = requireNotNull(member(memberId)) { "Member not found" }
        val membership = requireNotNull(memberships.firstOrNull { it.id == membershipId && it.memberId == memberId }) {
            "Renewal not found"
        }
        val plan = requireNotNull(plan(membership.planId)) { "Membership plan not found" }
        val payment = paymentsFor(memberId).firstOrNull { it.membershipId == membership.id }
        val due = outstandingBalance(membership.id)
        val message = buildList {
            add("Hi ${member.firstName},")
            add("")
            add("Thank you for renewing your ${plan.name} membership with ${organization.name}!")
            add("New validity: ${membership.startDate} to ${membership.endDate}")
            payment?.let { add("Payment received: ${organization.currencySymbol}${it.amount.asMoney()} (${it.receiptNumber})") }
            if (due > 0.009) add("Balance due: ${organization.currencySymbol}${due.asMoney()}")
            add("")
            add("We are happy to have you with us.")
            add("— ${organization.name}")
        }.joinToString("\n")
        return MemberMessageDraft(member.id, member.mobile, "Renewal thank you", message, membership.id)
    }

    fun recordExternalCommunication(
        draft: MemberMessageDraft,
        channel: CommunicationChannel,
        message: String
    ) {
        require(channel == CommunicationChannel.WHATSAPP || channel == CommunicationChannel.SMS) {
            "Only WhatsApp and SMS are supported"
        }
        val communication = Communication(
            id = UUID.randomUUID().toString(),
            memberId = draft.memberId,
            channel = channel,
            title = draft.title,
            message = message.trim(),
            status = "COMPOSER_OPENED",
            sentAt = currentDateTime(),
            membershipId = draft.membershipId,
            recipient = draft.recipient
        )
        mergeCommunication(communication)
        firebaseDataSource?.saveCommunication(communication)
        addActivity(draft.memberId, ActivityType.MESSAGE, draft.title, "${channel.pretty()} composer opened")
    }

    fun checkIn(memberId: String): Result<Visit> {
        val member = member(memberId) ?: return Result.failure(IllegalArgumentException("Member not found"))
        val status = memberStatus(memberId)
        if (status !in listOf(MemberStatus.ACTIVE, MemberStatus.EXPIRING_SOON)) {
            return Result.failure(IllegalStateException("${member.fullName}'s membership is ${status.pretty().lowercase()}"))
        }
        val existing = visits.firstOrNull { it.memberId == memberId && it.checkedOutAt == null && it.checkedInAt.toLocalDate() == currentDate() }
        if (existing != null) return Result.failure(IllegalStateException("${member.fullName} is already checked in"))
        val visit = Visit(UUID.randomUUID().toString(), organization.id, memberId, currentDateTime())
        visits.add(0, visit)
        firebaseDataSource?.saveVisit(visit)
        addActivity(memberId, ActivityType.VISIT, "Checked in", "Manual entry at ${organization.branchName}")
        return Result.success(visit)
    }

    @Synchronized
    fun addPlan(
        name: String,
        description: String,
        duration: Int,
        durationUnit: String,
        price: Double,
        registrationFee: Double = 0.0
    ): Result<String> = runCatching {
        val normalizedUnit = durationUnit.trim().uppercase(Locale.ROOT)
        require(name.isNotBlank()) { "Plan name is required" }
        require(duration > 0) { "Duration must be greater than zero" }
        require(normalizedUnit in setOf("DAY", "WEEK", "MONTH", "YEAR", "VISIT")) { "Select a valid duration unit" }
        require(price >= 0.0) { "Price cannot be negative" }
        require(registrationFee >= 0.0) { "Registration fee cannot be negative" }
        val normalizedName = name.trim()
        require(plans.none { it.name.equals(normalizedName, ignoreCase = true) }) {
            "A membership plan with this name already exists"
        }
        val planId = UUID.nameUUIDFromBytes(
            "${organization.id}:plan:${normalizedName.lowercase(Locale.ROOT)}".toByteArray()
        ).toString()
        val plan = MembershipPlan(planId, organization.id, normalizedName, description.trim(), duration, normalizedUnit, price, registrationFee)
        plans.add(plan)
        firebaseDataSource?.savePlan(plan)
        addActivity(null, ActivityType.SYSTEM, "Membership plan created", plan.name)
        plan.id
    }

    fun setPlanActive(planId: String, active: Boolean): Result<Unit> = runCatching {
        val index = plans.indexOfFirst { it.id == planId }
        require(index >= 0) { "Membership plan not found" }
        val updated = plans[index].copy(active = active)
        plans[index] = updated
        firebaseDataSource?.savePlan(updated)
        addActivity(null, ActivityType.SYSTEM, if (active) "Membership plan activated" else "Membership plan paused", updated.name)
    }

    @Synchronized
    fun addService(name: String, category: String) {
        require(name.isNotBlank()) { "Service name is required" }
        val normalizedName = name.trim()
        val normalizedCategory = category.trim().ifBlank { "General" }
        require(services.none {
            it.name.equals(normalizedName, ignoreCase = true) &&
                it.category.equals(normalizedCategory, ignoreCase = true)
        }) { "This service already exists" }
        val service = ServiceOffering(
            UUID.nameUUIDFromBytes(
                "${organization.id}:service:${normalizedCategory.lowercase(Locale.ROOT)}:${normalizedName.lowercase(Locale.ROOT)}".toByteArray()
            ).toString(),
            organization.id,
            normalizedName,
            normalizedCategory,
            "Custom service",
            "uses"
        )
        services.add(service)
        firebaseDataSource?.saveService(service)
        addActivity(null, ActivityType.SYSTEM, "Service created", service.name)
    }

    fun toggleRule(ruleId: String) {
        val index = automationRules.indexOfFirst { it.id == ruleId }
        if (index >= 0) {
            val updated = automationRules[index].copy(enabled = !automationRules[index].enabled)
            automationRules[index] = updated
            firebaseDataSource?.saveAutomationRule(updated)
            if (updated.enabled) processDueReminders()
        }
    }

    fun updateRuleChannel(ruleId: String, channel: CommunicationChannel) {
        require(channel in setOf(CommunicationChannel.PUSH, CommunicationChannel.WHATSAPP, CommunicationChannel.SMS)) {
            "Select Push, WhatsApp, or SMS"
        }
        val index = automationRules.indexOfFirst { it.id == ruleId }
        require(index >= 0) { "Automation rule not found" }
        val updated = automationRules[index].copy(channel = channel)
        automationRules[index] = updated
        firebaseDataSource?.saveAutomationRule(updated)
    }

    @Synchronized
    fun processDueReminders(today: LocalDate = currentDate()): Int {
        if (!isAutomationDataReady || workspaceProvisioningState == WorkspaceProvisioningState.LOADING) return 0
        var created = 0
        fun queue(
            member: Member,
            membership: Membership?,
            rule: AutomationRule,
            deliveryKey: String,
            message: String,
            templateKey: String,
            templateParameters: List<String>
        ) {
            if (communications.any { it.deliveryKey == deliveryKey }) return
            val communication = Communication(
                id = UUID.nameUUIDFromBytes(deliveryKey.toByteArray()).toString(),
                memberId = member.id,
                channel = rule.channel,
                title = rule.name,
                message = message,
                status = "READY",
                sentAt = currentDateTime(),
                membershipId = membership?.id,
                automationRuleId = rule.id,
                deliveryKey = deliveryKey,
                recipient = member.mobile.ifBlank { member.email },
                templateKey = templateKey,
                templateParameters = templateParameters
            )
            mergeCommunication(communication)
            firebaseDataSource?.saveCommunication(communication)
            addActivity(member.id, ActivityType.MESSAGE, rule.name, "${rule.channel.pretty()} • ready")
            created++
        }

        members.forEach { member ->
            val membership = membershipFor(member.id)
            if (membership != null && membership.status !in setOf(MembershipStatus.CANCELLED, MembershipStatus.FROZEN)) {
                val plan = plan(membership.planId)
                if (plan != null) {
                    val daysUntilExpiry = ChronoUnit.DAYS.between(today, membership.endDate).toInt()
                    val amountDue = outstandingBalance(membership.id)
                    automationRules.filter { it.enabled }.forEach { rule ->
                        val trigger = rule.triggerDaysBeforeExpiry
                        val matches = when (rule.event) {
                            "MEMBERSHIP_EXPIRING" -> trigger != null && trigger >= 0 && daysUntilExpiry == trigger
                            "MEMBERSHIP_EXPIRED" -> daysUntilExpiry < 0
                            "PAYMENT_DUE" -> amountDue > 0.009 && trigger != null &&
                                (if (trigger < 0) daysUntilExpiry < 0 else daysUntilExpiry == trigger)
                            else -> false
                        }
                        if (matches) {
                            val deliveryKey = "${membership.id}:${rule.id}:${membership.endDate}"
                            val message = rule.template
                                .replace("{{member_name}}", member.fullName)
                                .replace("{{membership_name}}", plan.name)
                                .replace("{{expiry_date}}", membership.endDate.toString())
                                .replace("{{amount_due}}", "${organization.currencySymbol}${amountDue.asMoney()}")
                                .replace("{{organization_name}}", organization.name)
                            val templateKey = when (rule.event) {
                                "MEMBERSHIP_EXPIRING" -> "membership_expiring"
                                "MEMBERSHIP_EXPIRED" -> "membership_expired"
                                "PAYMENT_DUE" -> "payment_due"
                                else -> ""
                            }
                            val templateParameters = when (rule.event) {
                                "PAYMENT_DUE" -> listOf(
                                    member.fullName,
                                    "${organization.currencySymbol}${amountDue.asMoney()}",
                                    plan.name,
                                    membership.endDate.toString(),
                                    organization.name
                                )
                                else -> listOf(
                                    member.fullName,
                                    plan.name,
                                    membership.endDate.toString(),
                                    organization.name
                                )
                            }
                            queue(member, membership, rule, deliveryKey, message, templateKey, templateParameters)
                        }
                    }
                }
            }

            member.dateOfBirth?.let { birthday ->
                if (birthday.month == today.month && birthday.dayOfMonth == today.dayOfMonth) {
                    automationRules.filter { it.enabled && it.event == "MEMBER_BIRTHDAY" }.forEach { rule ->
                        val deliveryKey = "${member.id}:${rule.id}:${today.year}"
                        val message = rule.template
                            .replace("{{member_name}}", member.fullName)
                            .replace("{{organization_name}}", organization.name)
                        queue(
                            member,
                            membership,
                            rule,
                            deliveryKey,
                            message,
                            "birthday",
                            listOf(member.fullName, organization.name)
                        )
                    }
                }
            }
        }
        lastAutomationCheckAt = currentDateTime()
        lastAutomationCreatedCount = created
        pendingAutomationNotificationCount += created
        return created
    }

    @Synchronized
    fun consumePendingAutomationNotificationCount(): Int = pendingAutomationNotificationCount.also {
        pendingAutomationNotificationCount = 0
    }

    @Synchronized
    fun automaticCommunicationsReady(): List<Communication> = communications.filter {
        it.status == "READY" &&
            it.automationRuleId != null &&
            it.templateKey.isNotBlank() &&
            it.recipient.isNotBlank() &&
            it.channel in setOf(CommunicationChannel.WHATSAPP, CommunicationChannel.SMS)
    }

    @Synchronized
    fun recordAutomaticDeliveryResult(
        communicationId: String,
        status: String,
        providerMessageId: String = "",
        failureReason: String = ""
    ) {
        val index = communications.indexOfFirst { it.id == communicationId }
        if (index < 0) return
        val updated = communications[index].copy(
            status = status,
            providerMessageId = providerMessageId,
            failureReason = failureReason.take(950)
        )
        communications[index] = updated
        firebaseDataSource?.saveCommunication(updated)
    }

    private fun markAutomationSourceLoaded(source: String) {
        loadedAutomationSources += source
        isAutomationDataReady = loadedAutomationSources.containsAll(requiredAutomationSources)
        if (isAutomationDataReady) processDueReminders()
    }

    fun updateOrganization(
        name: String,
        businessType: String,
        memberLabel: String,
        phone: String,
        email: String,
        currencyCode: String = organization.currencyCode,
        timezone: String = organization.timezone,
        branchName: String = organization.branchName,
        addressLine1: String = organization.addressLine1,
        addressLine2: String = organization.addressLine2,
        city: String = organization.city,
        state: String = organization.state,
        postalCode: String = organization.postalCode,
        countryCode: String = organization.countryCode
    ): Result<Unit> = runCatching {
        val normalizedCurrency = currencyCode.trim().uppercase(Locale.ROOT)
        val currency = Currency.getInstance(normalizedCurrency)
        val normalizedTimezone = ZoneId.of(timezone.trim()).id
        require(name.isNotBlank()) { "Organization name is required" }
        require(businessType.isNotBlank()) { "Business type is required" }
        require(branchName.isNotBlank()) { "Primary branch is required" }
        require(email.isBlank() || email.trim().matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) {
            "Enter a valid organization email"
        }
        require(countryCode.trim().length == 2 && countryCode.trim().all(Char::isLetter)) {
            "Enter a valid 2-letter country code"
        }
        organization = organization.copy(
            name = name.trim(),
            businessType = businessType.trim(),
            memberLabel = memberLabel.trim().ifBlank { "Member" },
            phone = phone.trim(),
            email = email.trim(),
            currencyCode = currency.currencyCode,
            currencySymbol = currency.getSymbol(Locale.getDefault()),
            timezone = normalizedTimezone,
            branchName = branchName.trim(),
            addressLine1 = addressLine1.trim(),
            addressLine2 = addressLine2.trim(),
            city = city.trim(),
            state = state.trim(),
            postalCode = postalCode.trim(),
            countryCode = countryCode.trim().uppercase(Locale.ROOT)
        )
        firebaseDataSource?.saveOrganization(organization)
        addActivity(null, ActivityType.SYSTEM, "Organization settings updated", "Profile, terminology and regional settings saved")
    }

    fun createOrganization(
        name: String,
        businessType: String,
        email: String,
        phone: String,
        branchName: String,
        addressLine1: String,
        addressLine2: String = "",
        city: String,
        state: String,
        postalCode: String,
        countryCode: String,
        currencyCode: String,
        timezone: String
    ): Result<Unit> = runCatching {
        require(email.isNotBlank()) { "Organization email is required" }
        require(addressLine1.isNotBlank()) { "Business address is required" }
        require(city.isNotBlank()) { "City is required" }
        updateOrganization(
            name = name,
            businessType = businessType,
            memberLabel = "Member",
            phone = phone,
            email = email,
            currencyCode = currencyCode,
            timezone = timezone,
            branchName = branchName,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            state = state,
            postalCode = postalCode,
            countryCode = countryCode
        ).getOrThrow()
        workspaceProvisioningState = WorkspaceProvisioningState.READY
        workspaceProvisioningError = null
        automationRules.forEach { firebaseDataSource?.saveAutomationRule(it) }
        val currentUser = com.membora.app.firebase.FirebaseServices.auth?.currentUser
        if (currentUser != null && staff.none { it.role == "Owner" }) {
            val owner = StaffMember(
                id = currentUser.uid,
                name = currentUser.displayName
                    ?: currentUser.email?.substringBefore('@')?.replaceFirstChar(Char::uppercase)
                    ?: "Organization owner",
                role = "Owner",
                phone = phone.trim(),
                active = true,
                email = currentUser.email.orEmpty(),
                branchName = branchName.trim(),
                invitationStatus = "ACTIVE"
            )
            staff.add(owner)
            firebaseDataSource?.saveStaff(owner)
        }
        addActivity(null, ActivityType.SYSTEM, "Organization created", "${organization.name} workspace provisioned")
    }

    fun uploadOrganizationLogo(
        bytes: ByteArray,
        contentType: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val path = "businesses/${organization.id}/branding/logo-${UUID.randomUUID()}.jpg"
        ImageKitUploadService.uploadCurrentUserFile(
            path = path,
            bytes = bytes,
            contentType = contentType,
            mediaType = MediaType.ORGANIZATION_LOGO
        ) { result ->
            result.fold(
                onSuccess = { upload ->
                    val updated = organization.copy(logoUrl = upload.url.toString())
                    val finishUpdate = {
                        organization = updated
                        FirebaseServices.deleteUnreferencedCurrentUserFiles(
                            directoryPath = "businesses/${organization.id}/branding",
                            keepUrls = setOf(updated.logoUrl)
                        ) { cleanup ->
                            addActivity(null, ActivityType.SYSTEM, "Organization logo updated", "Brand identity updated")
                            onResult(cleanup.mapFailure("Logo updated, but the previous logo could not be deleted"))
                        }
                    }
                    firebaseDataSource?.saveOrganization(updated) { saved ->
                        saved.fold(
                            onSuccess = { finishUpdate() },
                            onFailure = { error ->
                                FirebaseServices.deleteCurrentUserFile(upload.url.toString()) {
                                    onResult(Result.failure(error))
                                }
                            }
                        )
                    } ?: finishUpdate()
                },
                onFailure = { onResult(Result.failure(it)) }
            )
        }
    }

    fun inviteStaff(
        name: String,
        email: String,
        phone: String,
        role: String,
        branchName: String
    ): Result<String> = runCatching {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        require(name.isNotBlank()) { "Staff name is required" }
        require(normalizedEmail.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) { "Enter a valid staff email" }
        require(role in staffRoles) { "Select a valid role" }
        require(branchName.isNotBlank()) { "Branch is required" }
        require(staff.none { it.email.equals(normalizedEmail, ignoreCase = true) }) {
            "A staff member with this email already exists"
        }
        val member = StaffMember(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            role = role,
            phone = phone.trim(),
            active = true,
            email = normalizedEmail,
            branchName = branchName.trim(),
            invitationStatus = "PENDING"
        )
        staff.add(member)
        firebaseDataSource?.saveStaff(member)
        addActivity(null, ActivityType.SYSTEM, "Staff invitation created", "${member.email} • ${member.role}")
        member.id
    }

    fun setStaffActive(staffId: String, active: Boolean): Result<Unit> = runCatching {
        val index = staff.indexOfFirst { it.id == staffId }
        require(index >= 0) { "Staff member not found" }
        require(staff[index].role != "Owner" || active) { "The organization owner cannot be disabled" }
        val updated = staff[index].copy(
            active = active,
            invitationStatus = if (active) staff[index].invitationStatus.takeUnless { it == "DISABLED" } ?: "ACTIVE" else "DISABLED"
        )
        staff[index] = updated
        firebaseDataSource?.saveStaff(updated)
        addActivity(null, ActivityType.SYSTEM, if (active) "Staff access enabled" else "Staff access disabled", updated.email)
    }

    fun uploadMemberPhoto(
        memberId: String,
        bytes: ByteArray,
        contentType: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        val member = member(memberId)
            ?: return onResult(Result.failure(IllegalArgumentException("Member not found")))
        val extension = contentType?.substringAfter('/')?.substringBefore('+')?.takeIf { it.length <= 8 } ?: "jpg"
        val path = "businesses/${organization.id}/members/$memberId/photos/${UUID.randomUUID()}.$extension"
        ImageKitUploadService.uploadCurrentUserFile(
            path = path,
            bytes = bytes,
            contentType = contentType,
            mediaType = MediaType.MEMBER_PHOTO
        ) { result ->
            result.fold(
                onSuccess = { upload ->
                    val updated = member.copy(photoUrl = upload.url.toString())
                    val finishUpdate = {
                        mergeMember(updated)
                        FirebaseServices.deleteUnreferencedCurrentUserFiles(
                            directoryPath = "businesses/${organization.id}/members/$memberId/photos",
                            keepUrls = setOf(updated.photoUrl)
                        ) { cleanup ->
                            addActivity(memberId, ActivityType.MEMBER, "Profile photo updated", "Member photo uploaded")
                            onResult(cleanup.mapFailure("Photo updated, but the previous photo could not be deleted"))
                        }
                    }
                    firebaseDataSource?.saveMember(updated) { saved ->
                        saved.fold(
                            onSuccess = { finishUpdate() },
                            onFailure = { error ->
                                FirebaseServices.deleteCurrentUserFile(upload.url.toString()) {
                                    onResult(Result.failure(error))
                                }
                            }
                        )
                    } ?: finishUpdate()
                },
                onFailure = { onResult(Result.failure(it)) }
            )
        }
    }

    fun uploadMemberDocument(
        memberId: String,
        documentType: MemberDocumentType,
        fileName: String,
        bytes: ByteArray,
        contentType: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        val member = member(memberId)
            ?: return onResult(Result.failure(IllegalArgumentException("Member not found")))
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80).ifBlank { "document" }
        val path = "businesses/${organization.id}/members/$memberId/documents/${UUID.randomUUID()}-$safeName"
        ImageKitUploadService.uploadCurrentUserFile(
            path = path,
            bytes = bytes,
            contentType = contentType,
            mediaType = MediaType.MEMBER_DOCUMENT
        ) { result ->
            result.fold(
                onSuccess = { upload ->
                    val document = MemberDocument(
                        fileId = upload.fileId,
                        url = upload.url.toString(),
                        name = upload.name,
                        sizeBytes = upload.storedSizeBytes,
                        contentType = contentType.orEmpty(),
                        type = documentType,
                        uploadedAt = currentDateTime()
                    )
                    val replacedUrls = member.documents
                        .filter { it.type == documentType }
                        .mapTo(mutableSetOf()) { it.url }
                    val updated = member.copy(
                        documentUrls = (member.documentUrls.filterNot(replacedUrls::contains) + document.url).distinct(),
                        documents = member.documents.filterNot { it.type == documentType } + document
                    )
                    val finishUpdate = {
                        mergeMember(updated)
                        FirebaseServices.deleteUnreferencedCurrentUserFiles(
                            directoryPath = "businesses/${organization.id}/members/$memberId/documents",
                            keepUrls = updated.documentUrls.toSet()
                        ) { cleanup ->
                            addActivity(memberId, ActivityType.MEMBER, "${documentType.label} updated", upload.name)
                            onResult(cleanup.mapFailure("Document updated, but the previous file could not be deleted"))
                        }
                    }
                    firebaseDataSource?.saveMember(updated) { saved ->
                        saved.fold(
                            onSuccess = { finishUpdate() },
                            onFailure = { error ->
                                FirebaseServices.deleteCurrentUserFile(upload.url.toString()) {
                                    onResult(Result.failure(error))
                                }
                            }
                        )
                    } ?: finishUpdate()
                },
                onFailure = { onResult(Result.failure(it)) }
            )
        }
    }

    fun resolveDocumentUrl(url: String, onResult: (Result<android.net.Uri>) -> Unit) {
        ImageKitUploadService.resolvePrivateUrl(url, onResult)
    }

    private fun mergeMember(value: Member) {
        val index = members.indexOfFirst { it.id == value.id }
        if (index >= 0) members[index] = value else members.add(0, value)
    }

    private fun mergePlan(value: MembershipPlan) {
        val index = plans.indexOfFirst { it.id == value.id }
        if (index >= 0) plans[index] = value else plans.add(value)
    }

    private fun mergeMembership(value: Membership) {
        val index = memberships.indexOfFirst { it.id == value.id }
        if (index >= 0) memberships[index] = value else memberships.add(value)
    }

    private fun mergePayment(value: Payment) {
        val index = payments.indexOfFirst { it.id == value.id }
        if (index >= 0) payments[index] = value else payments.add(0, value)
    }

    private fun mergeCommunication(value: Communication) {
        val index = communications.indexOfFirst { it.id == value.id }
        if (index >= 0) communications[index] = value else communications.add(0, value)
    }

    private fun mergeStaff(value: StaffMember) {
        val index = staff.indexOfFirst { it.id == value.id }
        if (index >= 0) staff[index] = value else staff.add(value)
    }

    private fun grantPlanEntitlements(memberId: String, plan: MembershipPlan, validUntil: LocalDate) {
        plan.serviceIds.forEach { serviceId ->
            val quantity = when (serviceId) {
                "svc-access" -> 999
                "svc-guest" -> 2
                else -> 1
            }
            val entitlement = Entitlement(
                id = UUID.randomUUID().toString(),
                memberId = memberId,
                serviceId = serviceId,
                allocatedQuantity = quantity,
                usedQuantity = 0,
                validUntil = validUntil
            )
            entitlements.add(entitlement)
            firebaseDataSource?.saveEntitlement(entitlement)
        }
    }

    private fun addActivity(memberId: String?, type: ActivityType, title: String, detail: String) {
        val activity = ActivityEvent(UUID.randomUUID().toString(), memberId, type, title, detail, currentDateTime())
        activities.add(0, activity)
        firebaseDataSource?.saveActivity(activity)
    }

    private fun seedMembers() {
        val today = currentDate()
        members.addAll(listOf(
            Member("m-1", "org-1", "branch-main", "MEM-001001", "Rahul", "Kumar", "98765 12340", "rahul@example.com", "Bengaluru", joinedOn = today.minusMonths(4), emergencyContact = "Anita • 98765 12000", customValues = mapOf("Goal" to "Strength")),
            Member("m-2", "org-1", "branch-main", "MEM-001002", "Meera", "Iyer", "98765 12341", "meera@example.com", "Bengaluru", joinedOn = today.minusMonths(2), customValues = mapOf("Goal" to "Mobility")),
            Member("m-3", "org-1", "branch-main", "MEM-001003", "Arjun", "Nair", "98765 12342", "arjun@example.com", "Mysuru", joinedOn = today.minusMonths(8)),
            Member("m-4", "org-1", "branch-main", "MEM-001004", "Sara", "Khan", "98765 12343", "sara@example.com", "Bengaluru", joinedOn = today.minusDays(5)),
            Member("m-5", "org-1", "branch-main", "MEM-001005", "Neel", "Patel", "98765 12344", "neel@example.com", "Bengaluru", joinedOn = today.minusMonths(13)),
            Member("m-6", "org-1", "branch-main", "MEM-001006", "Tara", "Singh", "98765 12345", "tara@example.com", "Bengaluru", joinedOn = today.minusDays(1))
        ))
        fun addMembership(id: String, memberId: String, planId: String, start: LocalDate, end: LocalDate, paid: PaymentStatus) {
            val plan = plan(planId)!!
            memberships.add(Membership(id, organization.id, memberId, planId, start, end, MembershipStatus.ACTIVE, plan.price, 0.0, plan.price, paid, createdBy = "Diya Rao"))
        }
        addMembership("ms-1", "m-1", "plan-growth", today.minusMonths(2), today.plusMonths(1), PaymentStatus.PAID)
        addMembership("ms-2", "m-2", "plan-flex", today.minusDays(23), today.plusDays(6), PaymentStatus.PAID)
        addMembership("ms-3", "m-3", "plan-complete", today.minusMonths(8), today.plusMonths(4), PaymentStatus.PARTIAL)
        addMembership("ms-4", "m-4", "plan-growth", today.minusDays(5), today.plusMonths(3), PaymentStatus.PAID)
        addMembership("ms-5", "m-5", "plan-flex", today.minusMonths(2), today.minusMonths(1), PaymentStatus.DUE)
        addMembership("ms-6", "m-6", "plan-complete", today.minusDays(1), today.plusYears(1), PaymentStatus.PARTIAL)

        payments.addAll(listOf(
            Payment("pay-1", "org-1", "m-6", "ms-6", "RCPT-${today.year}-00003", 7500.0, PaymentMethod.UPI, "UPI839201", today.atTime(10, 35), PaymentStatus.PAID),
            Payment("pay-2", "org-1", "m-4", "ms-4", "RCPT-${today.year}-00002", 5249.0, PaymentMethod.CARD, "CARD9012", today.minusDays(2).atTime(17, 10), PaymentStatus.PAID),
            Payment("pay-3", "org-1", "m-2", "ms-2", "RCPT-${today.year}-00001", 1999.0, PaymentMethod.CASH, "", today.minusDays(3).atTime(9, 5), PaymentStatus.PAID),
            Payment("pay-4", "org-1", "m-3", "ms-3", "RCPT-${today.year - 1}-00142", 10000.0, PaymentMethod.BANK_TRANSFER, "NEFT12009", today.minusMonths(8).atTime(11, 30), PaymentStatus.PAID)
        ))
        visits.addAll(listOf(
            Visit("v-1", "org-1", "m-1", today.atTime(7, 42)),
            Visit("v-2", "org-1", "m-3", today.atTime(8, 16)),
            Visit("v-3", "org-1", "m-4", today.atTime(9, 3)),
            Visit("v-4", "org-1", "m-2", today.minusDays(1).atTime(18, 12), today.minusDays(1).atTime(19, 5))
        ))
        commitments.add(Commitment("c-1", "m-1", "Two complimentary coaching sessions", "Granted during quarterly renewal", 2, today.plusDays(28), "PENDING", "Diya Rao"))
        entitlements.addAll(listOf(
            Entitlement("e-1", "m-1", "svc-consult", 2, 1, today.plusMonths(1)),
            Entitlement("e-2", "m-3", "svc-coach", 12, 7, today.plusMonths(4)),
            Entitlement("e-3", "m-3", "svc-guest", 2, 0, today.plusMonths(4))
        ))
        communications.add(Communication("comm-1", "m-2", CommunicationChannel.WHATSAPP, "Expiry reminder", "Your Flex Monthly membership expires soon.", "DELIVERED", currentDateTime().minusHours(4)))
        activities.addAll(listOf(
            ActivityEvent("a-1", "m-6", ActivityType.MEMBER, "Member registered", "MEM-001006 created", currentDateTime().minusDays(1)),
            ActivityEvent("a-2", "m-6", ActivityType.PAYMENT, "Payment received", "₹7,500 via UPI", currentDateTime().minusDays(1).plusMinutes(5)),
            ActivityEvent("a-3", "m-2", ActivityType.MESSAGE, "Expiry reminder delivered", "WhatsApp • 7 days before expiry", currentDateTime().minusHours(4)),
            ActivityEvent("a-4", "m-4", ActivityType.VISIT, "Checked in", "Manual entry at Indiranagar", currentDateTime().minusHours(2))
        ).sortedByDescending { it.occurredAt })
    }

    private fun organizationZone(): ZoneId = runCatching { ZoneId.of(organization.timezone) }
        .getOrDefault(ZoneId.systemDefault())
}

fun Double.asMoney(): String = if (this % 1.0 == 0.0) {
    String.format(Locale.getDefault(), "%,.0f", this)
} else {
    String.format(Locale.getDefault(), "%,.2f", this)
}

fun Enum<*>.pretty(): String = name.lowercase(Locale.ROOT).replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun Result<Unit>.mapFailure(message: String): Result<Unit> = fold(
    onSuccess = { Result.success(Unit) },
    onFailure = { Result.failure(IllegalStateException(message, it)) }
)
