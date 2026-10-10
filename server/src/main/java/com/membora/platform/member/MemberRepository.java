package com.membora.platform.member;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, UUID> {
    Optional<Member> findByIdAndOrganizationId(UUID id, UUID organizationId);
    long countByOrganizationId(UUID organizationId);

    @Query("""
        select m from Member m where m.organizationId = :organizationId and
        (:query = '' or lower(concat(m.firstName, ' ', coalesce(m.lastName, ''))) like lower(concat('%', :query, '%'))
        or m.mobile like concat('%', :query, '%') or lower(m.memberCode) like lower(concat('%', :query, '%')))
        order by m.createdAt desc
        """)
    List<Member> search(@Param("organizationId") UUID organizationId, @Param("query") String query);
}
