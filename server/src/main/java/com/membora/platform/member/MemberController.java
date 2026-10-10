package com.membora.platform.member;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/members")
public class MemberController {
    private final MemberService service;
    public MemberController(MemberService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('MEMBER_READ')")
    List<Member> search(@RequestHeader("X-Organization-Id") UUID organizationId, @RequestParam(defaultValue = "") String q) {
        return service.search(organizationId, q);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MEMBER_READ')")
    Member get(@RequestHeader("X-Organization-Id") UUID organizationId, @PathVariable UUID id) { return service.get(organizationId, id); }

    @PostMapping
    @PreAuthorize("hasAuthority('MEMBER_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    Member create(@RequestHeader("X-Organization-Id") UUID organizationId, @Valid @RequestBody CreateMember request) {
        return service.create(organizationId, request.branchId(), request.firstName(), request.lastName(), request.mobile(), request.email(), request.city(), request.emergencyContact());
    }

    record CreateMember(UUID branchId, @NotBlank String firstName, String lastName, @NotBlank String mobile, String email, String city, String emergencyContact) {}
}
