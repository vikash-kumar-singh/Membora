package com.membora.platform.organization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {
    private final OrganizationRepository repository;
    private final com.membora.platform.auth.AuthService authService;

    public OrganizationController(OrganizationRepository repository, com.membora.platform.auth.AuthService authService) { this.repository = repository; this.authService = authService; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Organization create(@Valid @RequestBody CreateOrganization request) {
        var organization = repository.save(new Organization(request.name(), request.businessType(), request.memberLabel(), request.phone(), request.email()));
        authService.provisionOwner(organization.id, request.ownerEmail(), request.ownerPassword(), request.ownerName());
        return organization;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    Organization get(@RequestHeader("X-Organization-Id") UUID organizationId, @PathVariable UUID id) {
        if (!organizationId.equals(id)) throw new IllegalArgumentException("Organization not found");
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
    }

    record CreateOrganization(@NotBlank String name, @NotBlank String businessType, @NotBlank String memberLabel, String phone, @Email String email,
                              @Email @NotBlank String ownerEmail, @NotBlank String ownerPassword, @NotBlank String ownerName) {}
}
