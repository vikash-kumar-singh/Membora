package com.membora.platform.membership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, UUID> {
    List<MembershipPlan> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
    Optional<MembershipPlan> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
