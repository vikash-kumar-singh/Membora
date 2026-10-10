package com.membora.platform.membership;

import com.membora.platform.member.MemberRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {
    private final MembershipRepository memberships;
    private final MembershipPlanRepository plans;
    private final MemberRepository members;

    public MembershipService(MembershipRepository memberships, MembershipPlanRepository plans, MemberRepository members) {
        this.memberships = memberships;
        this.plans = plans;
        this.members = members;
    }

    @Transactional
    public Membership activate(UUID organizationId, UUID memberId, UUID planId, LocalDate requestedStart, BigDecimal discount, UUID staffId) {
        members.findByIdAndOrganizationId(memberId, organizationId).orElseThrow(() -> new IllegalArgumentException("Member not found"));
        var plan = plans.findByIdAndOrganizationId(planId, organizationId).orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        var start = requestedStart == null ? LocalDate.now() : requestedStart;
        var end = calculateEnd(start, plan);
        return memberships.save(new Membership(organizationId, memberId, planId, start, end, plan.price.add(plan.registrationFee), discount, staffId));
    }

    @Transactional
    public Membership renew(UUID organizationId, UUID membershipId, UUID planId, boolean consecutive, UUID staffId) {
        var current = require(organizationId, membershipId);
        var start = consecutive && !current.endDate.isBefore(LocalDate.now()) ? current.endDate.plusDays(1) : LocalDate.now();
        return activate(organizationId, current.memberId, planId, start, BigDecimal.ZERO, staffId);
    }

    @Transactional
    public Membership freeze(UUID organizationId, UUID membershipId, int days) {
        if (days < 1 || days > 365) throw new IllegalArgumentException("Freeze days must be between 1 and 365");
        var membership = require(organizationId, membershipId);
        if (effectiveStatus(membership) == MembershipStatus.EXPIRED) throw new IllegalStateException("Expired memberships cannot be frozen");
        membership.endDate = membership.endDate.plusDays(days);
        membership.status = MembershipStatus.FROZEN;
        return memberships.save(membership);
    }

    @Transactional
    public Membership cancel(UUID organizationId, UUID membershipId) {
        var membership = require(organizationId, membershipId);
        membership.status = MembershipStatus.CANCELLED;
        return memberships.save(membership);
    }

    @Transactional(readOnly = true)
    public Membership require(UUID organizationId, UUID id) {
        return memberships.findByIdAndOrganizationId(id, organizationId).orElseThrow(() -> new IllegalArgumentException("Membership not found"));
    }

    public MembershipStatus effectiveStatus(Membership membership) {
        if (membership.status == MembershipStatus.CANCELLED || membership.status == MembershipStatus.SUSPENDED || membership.status == MembershipStatus.FROZEN) return membership.status;
        var today = LocalDate.now();
        if (today.isBefore(membership.startDate)) return MembershipStatus.UPCOMING;
        if (today.isAfter(membership.endDate)) return MembershipStatus.EXPIRED;
        if (ChronoUnit.DAYS.between(today, membership.endDate) <= 7) return MembershipStatus.EXPIRING_SOON;
        return MembershipStatus.ACTIVE;
    }

    private LocalDate calculateEnd(LocalDate start, MembershipPlan plan) {
        return switch (plan.durationUnit.toUpperCase()) {
            case "DAY" -> start.plusDays(plan.durationValue).minusDays(1);
            case "VISIT" -> start.plusDays(60);
            default -> start.plusMonths(plan.durationValue).minusDays(1);
        };
    }
}
