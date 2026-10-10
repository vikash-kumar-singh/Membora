package com.membora.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.membora.platform.attendance.AttendanceService;
import com.membora.platform.auth.AuthService;
import com.membora.platform.member.MemberService;
import com.membora.platform.membership.*;
import com.membora.platform.organization.Organization;
import com.membora.platform.organization.OrganizationRepository;
import com.membora.platform.payment.PaymentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "membora.imagekit.private-key=test_private_key")
@Transactional
class CoreFlowIntegrationTest {
    @Autowired OrganizationRepository organizations;
    @Autowired MemberService members;
    @Autowired MembershipPlanRepository plans;
    @Autowired MembershipService memberships;
    @Autowired PaymentService payments;
    @Autowired AttendanceService attendance;
    @Autowired AuthService auth;

    @Test
    void organizationScopedOnboardingPaymentAndCheckInFlow() {
        var organization = organizations.save(new Organization("Test Club", "Club", "Member", "", "test@example.com"));
        var other = organizations.save(new Organization("Other Club", "Club", "Member", "", "other@example.com"));
        var member = members.create(organization.id, null, "Rahul", "Kumar", "9999999999", "", "Bengaluru", "");
        var plan = plans.save(new MembershipPlan(organization.id, "Quarterly", "", 3, "MONTH", new BigDecimal("5000.00"), BigDecimal.ZERO));
        var membership = memberships.activate(organization.id, member.id, plan.id, LocalDate.now(), BigDecimal.ZERO, UUID.randomUUID());

        var payment = payments.record(organization.id, member.id, membership.id, new BigDecimal("5000.00"), "UPI", "TEST-UPI", "", UUID.randomUUID());
        var visit = attendance.checkIn(organization.id, member.id, "QR");

        assertThat(payment.receiptNumber).startsWith("RCPT-");
        assertThat(visit.memberId).isEqualTo(member.id);
        assertThat(memberships.require(organization.id, membership.id).paymentStatus).isEqualTo("PAID");
        assertThatThrownBy(() -> members.get(other.id, member.id)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loginAndRefreshRotateCredentials() {
        var organization = organizations.save(new Organization("Secure Club", "Club", "Member", "", "secure@example.com"));
        auth.provisionOwner(organization.id, "owner@example.com", "StrongPass123!", "Owner");

        var login = auth.login("owner@example.com", "StrongPass123!", organization.id);
        var rotated = auth.refresh(login.refreshToken());

        assertThat(login.accessToken()).isNotBlank();
        assertThat(rotated.accessToken()).isNotBlank().isNotEqualTo(login.accessToken());
        assertThatThrownBy(() -> auth.refresh(login.refreshToken())).isInstanceOf(IllegalArgumentException.class);
    }
}
