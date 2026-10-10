package com.membora.platform.membership;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/memberships")
public class MembershipController {
    private final MembershipService service;
    public MembershipController(MembershipService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasAuthority('MEMBERSHIP_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    MembershipView activate(@RequestHeader("X-Organization-Id") UUID organizationId, @Valid @RequestBody ActivateMembership request) {
        return view(service.activate(organizationId, request.memberId(), request.planId(), request.startDate(), request.discount(), request.staffId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MEMBER_READ')")
    MembershipView get(@RequestHeader("X-Organization-Id") UUID organizationId, @PathVariable UUID id) { return view(service.require(organizationId, id)); }

    @PostMapping("/{id}/renew")
    @PreAuthorize("hasAuthority('MEMBERSHIP_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    MembershipView renew(@RequestHeader("X-Organization-Id") UUID organizationId, @PathVariable UUID id, @Valid @RequestBody RenewMembership request) {
        return view(service.renew(organizationId, id, request.planId(), request.consecutive(), request.staffId()));
    }

    @PostMapping("/{id}/freeze")
    @PreAuthorize("hasAuthority('MEMBERSHIP_WRITE')")
    MembershipView freeze(@RequestHeader("X-Organization-Id") UUID organizationId, @PathVariable UUID id, @Valid @RequestBody FreezeMembership request) { return view(service.freeze(organizationId, id, request.days())); }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('MEMBERSHIP_WRITE')")
    MembershipView cancel(@RequestHeader("X-Organization-Id") UUID organizationId, @PathVariable UUID id) { return view(service.cancel(organizationId, id)); }

    private MembershipView view(Membership membership) {
        return new MembershipView(membership.id, membership.memberId, membership.planId, membership.startDate, membership.endDate, service.effectiveStatus(membership), membership.finalAmount, membership.paymentStatus);
    }

    record ActivateMembership(@NotNull UUID memberId, @NotNull UUID planId, LocalDate startDate, BigDecimal discount, UUID staffId) {}
    record RenewMembership(@NotNull UUID planId, boolean consecutive, UUID staffId) {}
    record FreezeMembership(@Min(1) @Max(365) int days) {}
    record MembershipView(UUID id, UUID memberId, UUID planId, LocalDate startDate, LocalDate endDate, MembershipStatus status, BigDecimal finalAmount, String paymentStatus) {}
}
