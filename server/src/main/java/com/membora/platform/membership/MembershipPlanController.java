package com.membora.platform.membership;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/membership-plans")
public class MembershipPlanController {
    private final MembershipPlanRepository repository;
    public MembershipPlanController(MembershipPlanRepository repository) { this.repository = repository; }

    @GetMapping
    @PreAuthorize("hasAuthority('MEMBER_READ')")
    List<MembershipPlan> list(@RequestHeader("X-Organization-Id") UUID organizationId) { return repository.findByOrganizationIdOrderByCreatedAtDesc(organizationId); }

    @PostMapping
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    MembershipPlan create(@RequestHeader("X-Organization-Id") UUID organizationId, @Valid @RequestBody CreatePlan request) {
        return repository.save(new MembershipPlan(organizationId, request.name(), request.description(), request.durationValue(), request.durationUnit(), request.price(), request.registrationFee()));
    }

    record CreatePlan(@NotBlank String name, String description, @Positive int durationValue, @NotBlank String durationUnit, @Positive BigDecimal price, BigDecimal registrationFee) {}
}
