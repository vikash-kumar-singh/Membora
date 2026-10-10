package com.membora.platform.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserAccountRepository users; private final RoleRepository roles; private final OrganizationUserRepository memberships;
    private final RefreshTokenRepository refreshTokens; private final PasswordEncoder passwordEncoder; private final TokenService tokenService; private final long refreshDays;
    public AuthService(UserAccountRepository users, RoleRepository roles, OrganizationUserRepository memberships, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, TokenService tokenService, @Value("${membora.security.refresh-token-days}") long refreshDays) {
        this.users = users; this.roles = roles; this.memberships = memberships; this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder; this.tokenService = tokenService; this.refreshDays = refreshDays;
    }

    @Transactional
    public OrganizationUser provisionOwner(UUID organizationId, String email, String password, String displayName) {
        if (password == null || password.length() < 10) throw new IllegalArgumentException("Password must contain at least 10 characters");
        var user = users.findByEmailIgnoreCase(email).orElseGet(() -> users.save(new UserAccount(email, passwordEncoder.encode(password), displayName)));
        var ownerRole = roles.save(new Role(organizationId, "OWNER", Permissions.ALL));
        roles.save(new Role(organizationId, "ADMIN", Permissions.ALL));
        roles.save(new Role(organizationId, "MANAGER", Set.of(Permissions.MEMBER_READ, Permissions.MEMBER_WRITE, Permissions.MEMBERSHIP_WRITE, Permissions.PAYMENT_WRITE, Permissions.ATTENDANCE_WRITE, Permissions.REPORT_READ, Permissions.AUTOMATION_MANAGE)));
        roles.save(new Role(organizationId, "RECEPTIONIST", Set.of(Permissions.MEMBER_READ, Permissions.MEMBER_WRITE, Permissions.MEMBERSHIP_WRITE, Permissions.PAYMENT_WRITE, Permissions.ATTENDANCE_WRITE)));
        roles.save(new Role(organizationId, "VIEWER", Set.of(Permissions.MEMBER_READ, Permissions.REPORT_READ)));
        return memberships.save(new OrganizationUser(organizationId, user.id, null, ownerRole.id));
    }

    @Transactional
    public OrganizationUser inviteStaff(UUID organizationId, UUID branchId, String email, String displayName, String temporaryPassword, UUID roleId) {
        var role = roles.findByIdAndOrganizationId(roleId, organizationId).orElseThrow(() -> new IllegalArgumentException("Role not found"));
        var user = users.findByEmailIgnoreCase(email).orElseGet(() -> users.save(new UserAccount(email, passwordEncoder.encode(temporaryPassword), displayName)));
        if (memberships.findByOrganizationIdAndUserId(organizationId, user.id).isPresent()) throw new IllegalStateException("User already belongs to organization");
        return memberships.save(new OrganizationUser(organizationId, user.id, branchId, role.id));
    }

    @Transactional
    public TokenPair login(String email, String password, UUID organizationId) {
        var user = users.findByEmailIgnoreCase(email).orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!passwordEncoder.matches(password, user.passwordHash)) throw new IllegalArgumentException("Invalid credentials");
        var membership = memberships.findByOrganizationIdAndUserId(organizationId, user.id).filter(item -> "ACTIVE".equals(item.status)).orElseThrow(() -> new IllegalArgumentException("Invalid organization access"));
        var role = roles.findByIdAndOrganizationId(membership.roleId, organizationId).orElseThrow(() -> new IllegalStateException("Role not found"));
        return issue(user, membership, role);
    }

    @Transactional
    public TokenPair refresh(String rawToken) {
        var old = refreshTokens.findByTokenHash(hash(rawToken)).filter(RefreshToken::active).orElseThrow(() -> new IllegalArgumentException("Refresh token is invalid or expired"));
        var membership = memberships.findByIdAndStatus(old.organizationUserId, "ACTIVE").orElseThrow(() -> new IllegalArgumentException("Organization access is disabled"));
        var user = users.findById(old.userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        var role = roles.findByIdAndOrganizationId(membership.roleId, membership.organizationId).orElseThrow(() -> new IllegalStateException("Role not found"));
        old.revokedAt = Instant.now();
        var pair = issue(user, membership, role);
        old.replacedBy = pair.refreshTokenId(); refreshTokens.save(old);
        return pair;
    }

    @Transactional
    public void logout(String rawToken) { refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(token -> { token.revokedAt = Instant.now(); refreshTokens.save(token); }); }

    private TokenPair issue(UserAccount user, OrganizationUser membership, Role role) {
        var rawRefresh = UUID.randomUUID() + "." + UUID.randomUUID();
        var stored = refreshTokens.save(new RefreshToken(user.id, membership.id, hash(rawRefresh), Instant.now().plus(Duration.ofDays(refreshDays))));
        return new TokenPair(tokenService.accessToken(user, membership, role), rawRefresh, "Bearer", 900, stored.id);
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("Token hashing unavailable", exception); }
    }

    public record TokenPair(String accessToken, String refreshToken, String tokenType, long expiresIn, UUID refreshTokenId) {}
}
