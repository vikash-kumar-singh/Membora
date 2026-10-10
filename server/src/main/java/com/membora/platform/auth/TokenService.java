package com.membora.platform.auth;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final long accessMinutes;
    public TokenService(JwtEncoder encoder, @Value("${membora.security.access-token-minutes}") long accessMinutes) {
        this.encoder = encoder; this.accessMinutes = accessMinutes;
    }
    public String accessToken(UserAccount user, OrganizationUser membership, Role role) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("membora").id(UUID.randomUUID().toString()).issuedAt(now).expiresAt(now.plus(Duration.ofMinutes(accessMinutes)))
            .subject(user.id.toString()).claim("org_id", membership.organizationId.toString())
            .claim("organization_user_id", membership.id.toString()).claim("role", role.name)
            .claim("permissions", role.permissions.stream().sorted().toList()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
