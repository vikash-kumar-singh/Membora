package com.membora.platform.membership;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {
    Optional<Membership> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<Membership> findFirstByOrganizationIdAndMemberIdOrderByEndDateDesc(UUID organizationId, UUID memberId);
    List<Membership> findByOrganizationId(UUID organizationId);
    List<Membership> findByOrganizationIdAndEndDateBetween(UUID organizationId, LocalDate from, LocalDate to);
    long countByOrganizationId(UUID organizationId);
}
