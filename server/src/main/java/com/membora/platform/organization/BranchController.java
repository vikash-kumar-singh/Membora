package com.membora.platform.organization;

import com.membora.platform.auth.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {
    private final BranchRepository repository;
    public BranchController(BranchRepository repository) { this.repository = repository; }
    @GetMapping @PreAuthorize("hasAuthority('ORG_MANAGE') or hasAuthority('MEMBER_READ')")
    List<Branch> list(@RequestHeader("X-Organization-Id") UUID organizationId) { return repository.findByOrganizationIdOrderByName(organizationId); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('ORG_MANAGE')")
    Branch create(@RequestHeader("X-Organization-Id") UUID organizationId, @Valid @RequestBody CreateBranch request) {
        return repository.save(new Branch(organizationId, request.code(), request.name(), request.address(), request.city(), request.state(), request.country(), request.timezone()));
    }
    record CreateBranch(@NotBlank String code, @NotBlank String name, String address, String city, String state, String country, String timezone) {}
}
