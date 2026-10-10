package com.membora.platform.auth;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserAccountRepository extends JpaRepository<UserAccount, UUID> { Optional<UserAccount> findByEmailIgnoreCase(String email); }
interface RoleRepository extends JpaRepository<Role, UUID> { Optional<Role> findByIdAndOrganizationId(UUID id, UUID organizationId); List<Role> findByOrganizationId(UUID organizationId); }
interface OrganizationUserRepository extends JpaRepository<OrganizationUser, UUID> {
    Optional<OrganizationUser> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);
    Optional<OrganizationUser> findByIdAndStatus(UUID id, String status);
    List<OrganizationUser> findByOrganizationId(UUID organizationId);
}
interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> { Optional<RefreshToken> findByTokenHash(String tokenHash); }
