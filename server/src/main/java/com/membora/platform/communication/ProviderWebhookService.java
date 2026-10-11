package com.membora.platform.communication;

import com.membora.platform.communication.config.MessagingProviderConfig;
import com.membora.platform.communication.dto.WebhookResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ProviderWebhookService {
    private final CommunicationDeliveryRepository deliveries;
    private final MessagingProviderConfig.Properties properties;
    private final ObjectMapper objectMapper;

    public ProviderWebhookService(
        CommunicationDeliveryRepository deliveries,
        MessagingProviderConfig.Properties properties,
        ObjectMapper objectMapper
    ) {
        this.deliveries = deliveries;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String verifyMetaSubscription(String mode, String token, String challenge) {
        var configured = properties.webhooks().metaVerifyToken();
        if (!properties.webhooks().metaConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Meta webhook verification is not configured");
        }
        if (!"subscribe".equals(mode) || !constantEquals(configured, token) || challenge == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Meta webhook verification failed");
        }
        return challenge;
    }

    @Transactional
    public WebhookResult consumeMeta(String signatureHeader, String body) {
        var appSecret = properties.webhooks().metaAppSecret();
        if (!properties.webhooks().metaConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Meta webhook signature verification is not configured");
        }
        var expected = "sha256=" + hmacSha256(appSecret, body == null ? "" : body);
        if (!constantEquals(expected, signatureHeader)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Meta webhook signature is invalid");
        }

        var events = new ArrayList<ProviderEvent>();
        JsonNode root = readJson(body);
        for (var entry : root.path("entry")) {
            for (var change : entry.path("changes")) {
                for (var status : change.path("value").path("statuses")) {
                    events.add(new ProviderEvent(
                        status.path("id").asText(""),
                        status.path("status").asText(""),
                        metaError(status),
                        parseTimestamp(status.path("timestamp").asText(""))
                    ));
                }
            }
        }
        return apply(events);
    }

    @Transactional
    public WebhookResult consumeMsg91(String suppliedToken, String body, Map<String, String> parameters) {
        var configured = properties.webhooks().msg91Token();
        if (!properties.webhooks().msg91Configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MSG91 webhook verification is not configured");
        }
        if (!constantEquals(configured, suppliedToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "MSG91 webhook token is invalid");
        }

        JsonNode root = body == null || body.isBlank()
            ? objectMapper.valueToTree(parameters == null ? Map.of() : parameters)
            : readJson(body);
        if (root.path("data").isTextual()) root = readJson(root.path("data").asText());
        var nodes = new ArrayList<JsonNode>();
        if (root.isArray()) root.forEach(nodes::add);
        else nodes.add(root);

        var rootId = firstText(root, "requestId", "request_id", "requestID");
        var events = new ArrayList<ProviderEvent>();
        for (var node : nodes) {
            var nodeId = firstText(node, "requestId", "request_id", "requestID", "messageId", "message_id");
            if (nodeId.isBlank()) nodeId = rootId;
            if (node.path("report").isArray()) {
                for (var report : node.path("report")) events.add(msg91Event(report, nodeId));
            } else {
                events.add(msg91Event(node, nodeId));
            }
        }
        return apply(events);
    }

    private WebhookResult apply(List<ProviderEvent> events) {
        var updated = 0;
        var ignored = 0;
        for (var event : events) {
            if (event.providerMessageId().isBlank() || event.status().isBlank()) {
                ignored++;
                continue;
            }
            var delivery = deliveries.findByProviderMessageId(event.providerMessageId()).orElse(null);
            if (delivery == null) {
                ignored++;
                continue;
            }
            apply(delivery, event);
            deliveries.save(delivery);
            updated++;
        }
        return new WebhookResult(events.size(), updated, ignored);
    }

    private void apply(CommunicationDelivery delivery, ProviderEvent event) {
        var normalized = normalizeStatus(event.status());
        var eventAt = event.occurredAt() == null ? Instant.now() : event.occurredAt();
        var currentEvent = delivery.providerEventAt == null || !eventAt.isBefore(delivery.providerEventAt);
        delivery.webhookEventCount++;

        if (currentEvent) {
            delivery.providerStatus = event.status().trim().toUpperCase();
            delivery.providerEventAt = eventAt;
        }

        if (normalized.isBlank()) {
            delivery.updatedAt = Instant.now();
            return;
        } else if ("FAILED".equals(normalized)) {
            if (currentEvent && rank(delivery.status) < rank("DELIVERED")) {
                delivery.status = "FAILED";
                delivery.failedAt = eventAt;
                delivery.errorDetail = event.detail().isBlank()
                    ? "Provider reported delivery failure"
                    : event.detail().substring(0, Math.min(1000, event.detail().length()));
            }
        } else if (rank(normalized) > rank(delivery.status)
            && (!"FAILED".equals(delivery.status) || currentEvent && rank(normalized) >= rank("DELIVERED"))) {
            delivery.status = normalized;
            delivery.errorDetail = null;
        }

        if ("DELIVERED".equals(normalized) && delivery.deliveredAt == null) delivery.deliveredAt = eventAt;
        if ("READ".equals(normalized)) {
            if (delivery.deliveredAt == null) delivery.deliveredAt = eventAt;
            if (delivery.readAt == null) delivery.readAt = eventAt;
        }
        delivery.updatedAt = Instant.now();
    }

    private String normalizeStatus(String raw) {
        var status = raw == null ? "" : raw.trim().toLowerCase().replace('-', '_').replace(' ', '_');
        return switch (status) {
            case "1" -> "DELIVERED";
            case "2", "9", "16", "17", "25" -> "FAILED";
            case "read" -> "READ";
            case "delivered", "delivery_success", "success" -> "DELIVERED";
            case "sent" -> "SENT";
            case "accepted", "submitted", "queued", "pending", "request_sent" -> "SUBMITTED";
            case "failed", "failure", "undelivered", "rejected", "expired", "blocked" -> "FAILED";
            default -> "";
        };
    }

    private int rank(String status) {
        return switch (status == null ? "" : status) {
            case "READ" -> 4;
            case "DELIVERED" -> 3;
            case "SENT" -> 2;
            case "SUBMITTED", "PENDING" -> 1;
            default -> 0;
        };
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body == null ? "" : body);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook payload is invalid JSON");
        }
    }

    private String metaError(JsonNode status) {
        var errors = status.path("errors");
        if (!errors.isArray() || errors.isEmpty()) return "";
        var error = errors.get(0);
        return firstText(error, "message", "title", "details", "code");
    }

    private String firstText(JsonNode node, String... names) {
        for (var name : names) {
            var value = node.path(name);
            if (!value.isMissingNode() && !value.isNull()) {
                var text = value.asText("").trim();
                if (!text.isBlank()) return text;
            }
        }
        return "";
    }

    private Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            if (value.matches("[0-9]{10,13}")) {
                var number = Long.parseLong(value);
                return value.length() == 13 ? Instant.ofEpochMilli(number) : Instant.ofEpochSecond(number);
            }
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException ignored) {
                try {
                    return OffsetDateTime.parse(value).toInstant();
                } catch (DateTimeParseException ignoredOffset) {
                    return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        .atZone(ZoneId.of("Asia/Kolkata"))
                        .toInstant();
                }
            }
        } catch (DateTimeParseException | NumberFormatException ignored) {
            return null;
        }
    }

    private ProviderEvent msg91Event(JsonNode node, String fallbackId) {
        var providerId = firstText(node, "requestId", "request_id", "requestID", "messageId", "message_id");
        if (providerId.isBlank()) providerId = fallbackId;
        var status = firstText(
            node,
            "status",
            "deliveryStatus",
            "delivery_status",
            "eventName",
            "event",
            "upperCaseEventName",
            "description",
            "desc"
        );
        var detail = firstText(node, "failureReason", "failure_reason", "reason", "description", "desc");
        var timestamp = parseTimestamp(firstText(
            node,
            "timestamp",
            "date",
            "eventTime",
            "event_time",
            "deliveryTime",
            "ts"
        ));
        return new ProviderEvent(providerId, status, detail, timestamp);
    }

    private String hmacSha256(String secret, String body) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to verify Meta webhook signature", exception);
        }
    }

    private boolean constantEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private record ProviderEvent(String providerMessageId, String status, String detail, Instant occurredAt) {}
}
