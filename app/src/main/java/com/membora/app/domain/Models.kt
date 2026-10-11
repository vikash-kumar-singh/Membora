package com.membora.app.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class Organization(
    val id: String,
    val name: String,
    val businessType: String,
    val memberLabel: String,
    val currencySymbol: String,
    val timezone: String,
    val phone: String,
    val email: String,
    val branchName: String,
    val currencyCode: String = "INR",
    val logoUrl: String = "",
    val addressLine1: String = "",
    val addressLine2: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val countryCode: String = "IN",
    val taxId: String = "",
    val receiptFooter: String = "Thank you for your payment."
)

enum class MemberStatus { ACTIVE, EXPIRING_SOON, EXPIRED, UPCOMING, SUSPENDED }
enum class MembershipStatus { UPCOMING, ACTIVE, EXPIRING_SOON, EXPIRED, SUSPENDED, CANCELLED, FROZEN }
enum class PaymentStatus { PAID, PARTIAL, DUE, REFUNDED }
enum class PaymentMethod { CASH, UPI, CARD, BANK_TRANSFER, CHEQUE, OTHER }
enum class ActivityType { MEMBER, MEMBERSHIP, PAYMENT, VISIT, SERVICE, MESSAGE, SYSTEM }
enum class CommunicationChannel { PUSH, SMS, EMAIL, WHATSAPP, IN_APP }

enum class MemberDocumentType(val label: String) {
    ID_PROOF("Aadhaar / ID Proof"),
    MEDICAL_CERTIFICATE("Medical Certificate"),
    MEMBERSHIP_AGREEMENT("Membership Agreement"),
    OTHER("Other Document")
}

data class MemberDocument(
    val fileId: String,
    val url: String,
    val name: String,
    val sizeBytes: Long,
    val contentType: String,
    val type: MemberDocumentType,
    val uploadedAt: LocalDateTime
)

data class Member(
    val id: String,
    val organizationId: String,
    val branchId: String,
    val code: String,
    val firstName: String,
    val lastName: String,
    val mobile: String,
    val email: String,
    val city: String,
    val gender: String = "",
    val dateOfBirth: LocalDate? = null,
    val joinedOn: LocalDate,
    val emergencyContact: String = "",
    val notes: String = "",
    val alternatePhone: String = "",
    val addressLine: String = "",
    val postalCode: String = "",
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val emergencyContactRelationship: String = "",
    val customValues: Map<String, String> = emptyMap(),
    val photoUrl: String = "",
    val documentUrls: List<String> = emptyList(),
    val documents: List<MemberDocument> = emptyList()
) {
    val fullName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
    val initials: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
}

data class MembershipPlan(
    val id: String,
    val organizationId: String,
    val name: String,
    val description: String,
    val durationValue: Int,
    val durationUnit: String,
    val price: Double,
    val registrationFee: Double = 0.0,
    val active: Boolean = true,
    val serviceIds: List<String> = emptyList()
)

data class Membership(
    val id: String,
    val organizationId: String,
    val memberId: String,
    val planId: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: MembershipStatus,
    val amount: Double,
    val discount: Double,
    val finalAmount: Double,
    val paymentStatus: PaymentStatus,
    val autoRenew: Boolean = false,
    val createdBy: String,
    val frozenOn: LocalDate? = null,
    val freezeUntil: LocalDate? = null,
    val cancelledOn: LocalDate? = null,
    val statusReason: String = ""
)

data class ServiceOffering(
    val id: String,
    val organizationId: String,
    val name: String,
    val category: String,
    val description: String,
    val unitLabel: String,
    val active: Boolean = true
)

data class Entitlement(
    val id: String,
    val memberId: String,
    val serviceId: String,
    val allocatedQuantity: Int,
    val usedQuantity: Int,
    val validUntil: LocalDate
) {
    val remainingQuantity: Int get() = (allocatedQuantity - usedQuantity).coerceAtLeast(0)
}

data class Payment(
    val id: String,
    val organizationId: String,
    val memberId: String,
    val membershipId: String?,
    val receiptNumber: String,
    val amount: Double,
    val method: PaymentMethod,
    val reference: String,
    val paidOn: LocalDateTime,
    val status: PaymentStatus,
    val notes: String = ""
)

data class Visit(
    val id: String,
    val organizationId: String,
    val memberId: String,
    val checkedInAt: LocalDateTime,
    val checkedOutAt: LocalDateTime? = null,
    val method: String = "MANUAL"
)

data class Commitment(
    val id: String,
    val memberId: String,
    val title: String,
    val description: String,
    val quantity: Int,
    val validUntil: LocalDate,
    val status: String,
    val assignedBy: String
)

data class Communication(
    val id: String,
    val memberId: String,
    val channel: CommunicationChannel,
    val title: String,
    val message: String,
    val status: String,
    val sentAt: LocalDateTime,
    val membershipId: String? = null,
    val automationRuleId: String? = null,
    val deliveryKey: String = "",
    val recipient: String = "",
    val templateKey: String = "",
    val templateParameters: List<String> = emptyList(),
    val providerMessageId: String = "",
    val failureReason: String = ""
)

data class MemberMessageDraft(
    val memberId: String,
    val recipient: String,
    val title: String,
    val message: String,
    val membershipId: String? = null
)

data class AutomationRule(
    val id: String,
    val name: String,
    val event: String,
    val timing: String,
    val channel: CommunicationChannel,
    val template: String,
    val enabled: Boolean,
    val triggerDaysBeforeExpiry: Int? = null
)

data class StaffMember(
    val id: String,
    val name: String,
    val role: String,
    val phone: String,
    val active: Boolean,
    val email: String = "",
    val branchName: String = "Main",
    val invitationStatus: String = if (active) "ACTIVE" else "DISABLED",
    val firebaseUid: String = "",
    val invitationId: String = ""
)

data class ActivityEvent(
    val id: String,
    val memberId: String?,
    val type: ActivityType,
    val title: String,
    val detail: String,
    val occurredAt: LocalDateTime
)

data class NewMemberRequest(
    val firstName: String,
    val lastName: String,
    val mobile: String,
    val email: String,
    val city: String,
    val emergencyContact: String,
    val planId: String?,
    val paymentAmount: Double,
    val paymentMethod: PaymentMethod,
    val gender: String = "",
    val dateOfBirth: LocalDate? = null,
    val alternatePhone: String = "",
    val addressLine: String = "",
    val postalCode: String = "",
    val notes: String = "",
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val emergencyContactRelationship: String = "",
    val customValues: Map<String, String> = emptyMap(),
    val membershipStartDate: LocalDate? = null,
    val membershipEndDate: LocalDate? = null
)
