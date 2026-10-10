package com.membora.platform.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.membora.platform.media.config.ImageKitConfig;
import io.imagekit.client.okhttp.ImageKitOkHttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class ImageKitServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");
    private static final String PRIVATE_KEY = "test_private_key";

    @Test
    void createsSdkAuthenticationWithoutExposingPrivateKey() throws Exception {
        var service = service(Clock.systemUTC());

        var auth = service.createUploadAuthentication("firebase_user-1");

        assertThat(auth.token()).isNotBlank();
        assertThat(auth.expire()).isGreaterThan(Instant.now().getEpochSecond());
        assertThat(auth.signature()).isEqualTo(signature(PRIVATE_KEY, auth.token() + auth.expire()));
        assertThat(auth.publicKey()).isEqualTo("test_public_key");
        assertThat(auth.urlEndpoint()).isEqualTo("https://ik.imagekit.io/membora");
        assertThat(auth.folder()).isEqualTo("/membora/users/firebase_user-1");
        assertThat(auth.toString()).doesNotContain(PRIVATE_KEY);
    }

    @Test
    void rejectsMissingServerConfiguration() {
        var missingSecret = new ImageKitConfig.Properties(
            "",
            "test_public_key",
            "https://ik.imagekit.io/membora"
        );

        assertThatThrownBy(missingSecret::requiredPrivateKey)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("IMAGEKIT_PRIVATE_KEY");
    }

    @Test
    void signsOnlyPrivateUrlsOwnedByTheAuthenticatedFirebaseUser() throws Exception {
        var service = service(Clock.fixed(NOW, ZoneOffset.UTC));
        var raw = "https://ik.imagekit.io/membora/membora/users/user-1/businesses/org-1/members/member-1/documents/file.pdf";

        var signed = service.createSignedUrl("user-1", raw);

        var expiry = NOW.plusSeconds(600).getEpochSecond();
        var relative = "membora/users/user-1/businesses/org-1/members/member-1/documents/file.pdf";
        assertThat(signed.expiresAt()).isEqualTo(expiry);
        assertThat(signed.url()).isEqualTo(
            raw + "?ik-t=" + expiry + "&ik-s=" + signature(PRIVATE_KEY, relative + expiry)
        );
        assertThatThrownBy(() -> service.createSignedUrl("other-user", raw))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createSignedUrl(
            "user-1",
            "https://ik.imagekit.io/membora/membora/users/user-1/../other-user/private.pdf"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createSignedUrl(
            "user-1",
            raw + "?tr=w-100"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private ImageKitService service(Clock clock) {
        var client = ImageKitOkHttpClient.builder().privateKey(PRIVATE_KEY).build();
        var properties = new ImageKitConfig.Properties(
            PRIVATE_KEY,
            "test_public_key",
            "https://ik.imagekit.io/membora/"
        );
        return new ImageKitService(client, properties, clock);
    }

    private String signature(String key, String value) throws Exception {
        var mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
