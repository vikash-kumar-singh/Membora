package com.membora.platform.subscription;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Verifies the OIDC token attached by the dedicated Google Pub/Sub push service account. */
@Component
public class GooglePubSubIdentityVerifier {
    private static final String GOOGLE_JWK_SET = "https://www.googleapis.com/oauth2/v3/certs";

    private final JwtDecoder decoder;
    private final String audience;
    private final String serviceAccountEmail;

    public GooglePubSubIdentityVerifier(
        @Value("${membora.play.rtdn-audience:}") String audience,
        @Value("${membora.play.rtdn-service-account-email:}") String serviceAccountEmail
    ) {
        this.audience = audience.trim();
        this.serviceAccountEmail = serviceAccountEmail.trim();
        var jwtDecoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET).build();
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer("https://accounts.google.com");
        OAuth2TokenValidator<Jwt> claims = jwt -> validClaims(jwt)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "Pub/Sub push identity is invalid", null
            ));
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(issuer, claims)));
        this.decoder = jwtDecoder;
    }

    public void requireAuthorized(String authorizationHeader) {
        if (audience.isBlank() || serviceAccountEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Google Play notifications are not configured");
        }
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Pub/Sub bearer token is required");
        }
        try {
            decoder.decode(authorizationHeader.substring("Bearer ".length()).trim());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Pub/Sub bearer token is invalid");
        }
    }

    private boolean validClaims(Jwt jwt) {
        return !audience.isBlank() && !serviceAccountEmail.isBlank() &&
            jwt.getAudience().contains(audience) &&
            serviceAccountEmail.equalsIgnoreCase(jwt.getClaimAsString("email")) &&
            Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));
    }
}
