package com.membora.platform.media;

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

/** Verifies Firebase ID tokens without accepting an untrusted user id from the client. */
@Component
public class FirebaseIdentityVerifier {
    private static final String FIREBASE_JWK_SET =
        "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

    private final JwtDecoder decoder;

    public FirebaseIdentityVerifier(@Value("${membora.firebase.project-id}") String projectId) {
        var jwtDecoder = NimbusJwtDecoder.withJwkSetUri(FIREBASE_JWK_SET).build();
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(
            "https://securetoken.google.com/" + projectId
        );
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains(projectId)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "Firebase token audience is invalid", null
            ));
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(issuer, audience)));
        this.decoder = jwtDecoder;
    }

    public String requireUser(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firebase ID token is required");
        }
        try {
            var jwt = decoder.decode(rawToken.trim());
            var firebase = jwt.getClaimAsMap("firebase");
            var provider = firebase == null ? null : firebase.get("sign_in_provider");
            if ("anonymous".equals(provider)) {
                throw new IllegalArgumentException("Anonymous accounts cannot upload files");
            }
            var subject = jwt.getSubject();
            if (subject == null || subject.isBlank()) throw new IllegalArgumentException("Token subject is missing");
            return subject;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firebase session is invalid or expired");
        }
    }
}
