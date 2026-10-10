package com.membora.platform.media.service;

import com.membora.platform.media.config.ImageKitConfig;
import com.membora.platform.media.dto.ImageKitAuthResponse;
import com.membora.platform.media.dto.ImageKitSignedUrlResponse;
import io.imagekit.client.ImageKitClient;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Provides authenticated upload parameters and scoped private-media delivery URLs. */
@Service
public class ImageKitService {
    private static final Duration SIGNED_URL_VALIDITY = Duration.ofMinutes(10);

    private final ImageKitClient client;
    private final ImageKitConfig.Properties properties;
    private final Clock clock;

    @Autowired
    public ImageKitService(ImageKitClient client, ImageKitConfig.Properties properties) {
        this(client, properties, Clock.systemUTC());
    }

    ImageKitService(ImageKitClient client, ImageKitConfig.Properties properties, Clock clock) {
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    public ImageKitAuthResponse createUploadAuthentication(String firebaseUserId) {
        var userId = safeSegment(firebaseUserId);
        Map<String, Object> authentication = client.helper().getAuthenticationParameters(null, null);
        var token = requiredString(authentication, "token");
        var signature = requiredString(authentication, "signature");
        var expireValue = authentication.get("expire");
        if (!(expireValue instanceof Number expire) || expire.longValue() <= clock.instant().getEpochSecond()) {
            throw new IllegalStateException("ImageKit returned an invalid upload expiry");
        }
        return new ImageKitAuthResponse(
            token,
            expire.longValue(),
            signature,
            properties.requiredPublicKey(),
            properties.requiredUrlEndpoint(),
            "/membora/users/" + userId
        );
    }

    public ImageKitSignedUrlResponse createSignedUrl(String firebaseUserId, String rawUrl) {
        var urlEndpoint = properties.requiredUrlEndpoint();
        var ownerPrefix = urlEndpoint + "/membora/users/" + safeSegment(firebaseUserId) + "/";
        URI endpoint;
        URI requested;
        try {
            endpoint = URI.create(urlEndpoint + "/");
            requested = URI.create(rawUrl == null ? "" : rawUrl).normalize();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Document URL is invalid", exception);
        }
        var rawPath = requested.getRawPath();
        var ownerPath = URI.create(ownerPrefix).getRawPath();
        var invalidLocation = !"https".equalsIgnoreCase(requested.getScheme())
            || !Objects.equals(endpoint.getHost(), requested.getHost())
            || endpoint.getPort() != requested.getPort()
            || requested.getUserInfo() != null
            || requested.getQuery() != null
            || requested.getFragment() != null;
        var invalidPath = rawPath == null
            || rawPath.contains("%")
            || rawPath.contains("\\")
            || !rawPath.startsWith(ownerPath)
            || !rawPath.startsWith(endpoint.getRawPath());
        if (invalidLocation || invalidPath) {
            throw new IllegalArgumentException("Document URL is outside this user's workspace");
        }
        var expiresAt = clock.instant().plus(SIGNED_URL_VALIDITY).getEpochSecond();
        var relativeUrl = rawPath.substring(endpoint.getRawPath().length());
        var signature = sign(relativeUrl + expiresAt);
        return new ImageKitSignedUrlResponse(
            requested.toASCIIString() + "?ik-t=" + expiresAt + "&ik-s=" + signature,
            expiresAt
        );
    }

    private String sign(String value) {
        try {
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(
                properties.requiredPrivateKey().getBytes(StandardCharsets.UTF_8),
                "HmacSHA1"
            ));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign the ImageKit URL", exception);
        }
    }

    private String requiredString(Map<String, Object> values, String key) {
        var value = values.get(key);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalStateException("ImageKit returned invalid authentication parameters");
        }
        return string;
    }

    private String safeSegment(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("Firebase user id is invalid");
        }
        return value;
    }
}
