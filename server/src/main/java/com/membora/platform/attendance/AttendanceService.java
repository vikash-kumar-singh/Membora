package com.membora.platform.attendance;

import com.membora.platform.member.MemberRepository;
import com.membora.platform.membership.MembershipService;
import com.membora.platform.membership.MembershipStatus;
import com.membora.platform.membership.MembershipRepository;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceService {
    private final VisitRepository visits;
    private final MemberRepository members;
    private final MembershipRepository memberships;
    private final MembershipService membershipService;

    public AttendanceService(VisitRepository visits, MemberRepository members, MembershipRepository memberships, MembershipService membershipService) {
        this.visits = visits;
        this.members = members;
        this.memberships = memberships;
        this.membershipService = membershipService;
    }

    @Transactional
    public Visit checkIn(UUID organizationId, UUID memberId, String method) {
        members.findByIdAndOrganizationId(memberId, organizationId).orElseThrow(() -> new IllegalArgumentException("Member not found"));
        var membership = memberships.findFirstByOrganizationIdAndMemberIdOrderByEndDateDesc(organizationId, memberId).orElseThrow(() -> new IllegalStateException("No membership found"));
        var status = membershipService.effectiveStatus(membership);
        if (status != MembershipStatus.ACTIVE && status != MembershipStatus.EXPIRING_SOON) throw new IllegalStateException("Membership is " + status.name().toLowerCase());
        var startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        if (visits.existsByOrganizationIdAndMemberIdAndCheckedInAtAfterAndCheckedOutAtIsNull(organizationId, memberId, startOfDay)) throw new IllegalStateException("Member is already checked in");
        return visits.save(new Visit(organizationId, memberId, method == null ? "MANUAL" : method));
    }
}
