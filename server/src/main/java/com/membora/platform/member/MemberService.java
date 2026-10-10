package com.membora.platform.member;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {
    private final MemberRepository repository;

    public MemberService(MemberRepository repository) { this.repository = repository; }

    @Transactional
    public Member create(UUID organizationId, UUID branchId, String firstName, String lastName, String mobile, String email, String city, String emergencyContact) {
        var next = repository.countByOrganizationId(organizationId) + 1001;
        var code = "MEM-%06d".formatted(next);
        return repository.save(new Member(organizationId, branchId, code, firstName.trim(), lastName == null ? "" : lastName.trim(), mobile.trim(), email, city, emergencyContact));
    }

    @Transactional(readOnly = true)
    public Member get(UUID organizationId, UUID id) {
        return repository.findByIdAndOrganizationId(id, organizationId).orElseThrow(() -> new IllegalArgumentException("Member not found"));
    }

    @Transactional(readOnly = true)
    public List<Member> search(UUID organizationId, String query) { return repository.search(organizationId, query == null ? "" : query.trim()); }
}
