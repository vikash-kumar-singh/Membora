package com.membora.platform.payment;

import com.membora.platform.member.MemberRepository;
import com.membora.platform.membership.MembershipRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final MemberRepository members;
    private final MembershipRepository memberships;

    public PaymentService(PaymentRepository payments, MemberRepository members, MembershipRepository memberships) {
        this.payments = payments;
        this.members = members;
        this.memberships = memberships;
    }

    @Transactional
    public Payment record(UUID organizationId, UUID memberId, UUID membershipId, BigDecimal amount, String method, String reference, String notes, UUID staffId) {
        members.findByIdAndOrganizationId(memberId, organizationId).orElseThrow(() -> new IllegalArgumentException("Member not found"));
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Payment amount must be positive");
        var receipt = "RCPT-%d-%06d".formatted(LocalDate.now().getYear(), payments.countByOrganizationId(organizationId) + 1);
        var payment = payments.save(new Payment(organizationId, memberId, membershipId, receipt, amount, method, reference, notes, staffId));
        if (membershipId != null) {
            var membership = memberships.findByIdAndOrganizationId(membershipId, organizationId).orElseThrow(() -> new IllegalArgumentException("Membership not found"));
            if (!membership.memberId.equals(memberId)) throw new IllegalArgumentException("Membership does not belong to member");
            var totalPaid = payments.paidForMembership(organizationId, membershipId);
            membership.paymentStatus = totalPaid.compareTo(membership.finalAmount) >= 0 ? "PAID" : "PARTIAL";
            memberships.save(membership);
        }
        return payment;
    }
}
