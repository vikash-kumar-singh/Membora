package com.membora.platform.organization;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BranchRepository extends JpaRepository<Branch, UUID> {
    List<Branch> findByOrganizationIdOrderByName(UUID organizationId);
    Optional<Branch> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
