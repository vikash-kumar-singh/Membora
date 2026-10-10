package com.membora.platform.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByOrganizationIdOrderByPaidAtDesc(UUID organizationId);
    long countByOrganizationId(UUID organizationId);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.organizationId = :organizationId and p.membershipId = :membershipId and p.status = 'PAID'")
    BigDecimal paidForMembership(@Param("organizationId") UUID organizationId, @Param("membershipId") UUID membershipId);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.organizationId = :organizationId and p.paidAt >= :from")
    BigDecimal revenueSince(@Param("organizationId") UUID organizationId, @Param("from") Instant from);
}
