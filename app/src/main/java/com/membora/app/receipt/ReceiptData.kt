package com.membora.app.receipt

import com.membora.app.data.MemboraRepository
import com.membora.app.domain.PaymentMethod
import java.time.LocalDateTime
import kotlin.math.max

data class ReceiptData(
    val receiptNumber: String,
    val paidOn: LocalDateTime,
    val organizationName: String,
    val branchName: String,
    val organizationAddress: String,
    val organizationPhone: String,
    val organizationEmail: String,
    val taxId: String,
    val receiptFooter: String,
    val memberName: String,
    val memberCode: String,
    val memberPhone: String,
    val memberEmail: String,
    val planName: String,
    val membershipPeriod: String,
    val membershipAmount: Double,
    val registrationFee: Double,
    val discount: Double,
    val membershipTotal: Double,
    val amountReceived: Double,
    val cumulativePaid: Double,
    val balanceAfterPayment: Double,
    val paymentMethod: PaymentMethod,
    val paymentReference: String,
    val notes: String,
    val receivedBy: String,
    val currencyCode: String,
    val currencySymbol: String
)

fun MemboraRepository.receiptData(paymentId: String): ReceiptData {
    val payment = requireNotNull(payments.firstOrNull { it.id == paymentId }) { "Payment not found" }
    val member = requireNotNull(member(payment.memberId)) { "Member not found" }
    val membership = payment.membershipId?.let { id -> memberships.firstOrNull { it.id == id } }
    val plan = membership?.let { plan(it.planId) }
    val cumulativePaid = payments
        .filter { it.membershipId == payment.membershipId && !it.paidOn.isAfter(payment.paidOn) }
        .sumOf { it.amount }
    val address = listOf(
        organization.addressLine1,
        organization.addressLine2,
        listOf(organization.city, organization.state, organization.postalCode).filter(String::isNotBlank).joinToString(", "),
        organization.countryCode
    ).filter(String::isNotBlank).joinToString("\n")
    return ReceiptData(
        receiptNumber = payment.receiptNumber,
        paidOn = payment.paidOn,
        organizationName = organization.name,
        branchName = organization.branchName,
        organizationAddress = address,
        organizationPhone = organization.phone,
        organizationEmail = organization.email,
        taxId = organization.taxId,
        receiptFooter = organization.receiptFooter,
        memberName = member.fullName,
        memberCode = member.code,
        memberPhone = member.mobile,
        memberEmail = member.email,
        planName = plan?.name ?: "General payment",
        membershipPeriod = membership?.let { "${it.startDate} to ${it.endDate}" }.orEmpty(),
        membershipAmount = membership?.amount ?: payment.amount,
        registrationFee = plan?.registrationFee ?: 0.0,
        discount = membership?.discount ?: 0.0,
        membershipTotal = membership?.finalAmount ?: payment.amount,
        amountReceived = payment.amount,
        cumulativePaid = cumulativePaid,
        balanceAfterPayment = max(0.0, (membership?.finalAmount ?: payment.amount) - cumulativePaid),
        paymentMethod = payment.method,
        paymentReference = payment.reference,
        notes = payment.notes,
        receivedBy = membership?.createdBy.orEmpty().ifBlank { "Authorized staff" },
        currencyCode = organization.currencyCode,
        currencySymbol = organization.currencySymbol
    )
}
