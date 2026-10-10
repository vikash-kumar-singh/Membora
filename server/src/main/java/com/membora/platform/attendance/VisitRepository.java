package com.membora.platform.attendance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitRepository extends JpaRepository<Visit, UUID> {
    List<Visit> findByOrganizationIdAndCheckedInAtAfterOrderByCheckedInAtDesc(UUID organizationId, Instant since);
    boolean existsByOrganizationIdAndMemberIdAndCheckedInAtAfterAndCheckedOutAtIsNull(UUID organizationId, UUID memberId, Instant since);
}
