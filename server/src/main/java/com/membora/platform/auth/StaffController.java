package com.membora.platform.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/staff")
@PreAuthorize("hasAuthority('STAFF_MANAGE')")
public class StaffController {
    private final OrganizationUserRepository memberships; private final UserAccountRepository users; private final RoleRepository roles; private final AuthService auth;
    public StaffController(OrganizationUserRepository memberships, UserAccountRepository users, RoleRepository roles, AuthService auth) { this.memberships = memberships; this.users = users; this.roles = roles; this.auth = auth; }
    @GetMapping
    List<StaffView> list(@RequestHeader("X-Organization-Id") UUID organizationId) {
        return memberships.findByOrganizationId(organizationId).stream().map(item -> {
            var user = users.findById(item.userId).orElseThrow(); var role = roles.findById(item.roleId).orElseThrow();
            return new StaffView(item.id, user.id, user.displayName, user.email, role.id, role.name, item.branchId, item.status);
        }).toList();
    }
    @GetMapping("/roles") List<Role> roles(@RequestHeader("X-Organization-Id") UUID organizationId) { return roles.findByOrganizationId(organizationId); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    OrganizationUser invite(@RequestHeader("X-Organization-Id") UUID organizationId, @Valid @RequestBody InviteStaff request) {
        return auth.inviteStaff(organizationId, request.branchId(), request.email(), request.displayName(), request.temporaryPassword(), request.roleId());
    }
    record InviteStaff(UUID branchId, @Email @NotBlank String email, @NotBlank String displayName, @Size(min=10) String temporaryPassword, @NotNull UUID roleId) {}
    record StaffView(UUID membershipId, UUID userId, String name, String email, UUID roleId, String role, UUID branchId, String status) {}
}
