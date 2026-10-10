package com.membora.platform.report;

import com.membora.platform.attendance.VisitRepository;
import com.membora.platform.member.MemberRepository;
import com.membora.platform.membership.MembershipRepository;
import com.membora.platform.membership.MembershipService;
import com.membora.platform.membership.MembershipStatus;
import com.membora.platform.payment.PaymentRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/reports")
public class DashboardController {
    private final MemberRepository members;
    private final MembershipRepository memberships;
    private final MembershipService membershipService;
    private final PaymentRepository payments;
    private final VisitRepository visits;

    public DashboardController(MemberRepository members, MembershipRepository memberships, MembershipService membershipService, PaymentRepository payments, VisitRepository visits) {
        this.members = members;
        this.memberships = memberships;
        this.membershipService = membershipService;
        this.payments = payments;
        this.visits = visits;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    Dashboard dashboard(@RequestHeader("X-Organization-Id") UUID organizationId) {
        var allMemberships = memberships.findByOrganizationId(organizationId);
        var active = allMemberships.stream().filter(item -> membershipService.effectiveStatus(item) == MembershipStatus.ACTIVE).count();
        var expiring = allMemberships.stream().filter(item -> membershipService.effectiveStatus(item) == MembershipStatus.EXPIRING_SOON).count();
        var expired = allMemberships.stream().filter(item -> membershipService.effectiveStatus(item) == MembershipStatus.EXPIRED).count();
        var startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        var revenue = payments.revenueSince(organizationId, startOfDay);
        var todayVisits = visits.findByOrganizationIdAndCheckedInAtAfterOrderByCheckedInAtDesc(organizationId, startOfDay).size();
        return new Dashboard(members.countByOrganizationId(organizationId), active, expiring, expired, revenue, todayVisits);
    }

    record Dashboard(long totalMembers, long activeMemberships, long expiringSoon, long expired, BigDecimal todayRevenue, long todayVisits) {}
}
