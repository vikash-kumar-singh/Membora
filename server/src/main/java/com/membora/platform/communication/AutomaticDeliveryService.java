package com.membora.platform.communication;

import com.membora.platform.communication.config.MessagingProviderConfig;
import com.membora.platform.communication.dto.AutomaticDeliveryRequest;
import com.membora.platform.communication.dto.AutomaticDeliveryResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class AutomaticDeliveryService {
    private final CommunicationDeliveryRepository deliveries;
    private final MessagingProviderConfig.Properties properties;
    private final RestClient restClient;

    public AutomaticDeliveryService(
        CommunicationDeliveryRepository deliveries,
        MessagingProviderConfig.Properties properties,
        RestClient.Builder restClientBuilder
    ) {
        this.deliveries = deliveries;
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Transactional
    public AutomaticDeliveryResponse deliver(String firebaseUserId, AutomaticDeliveryRequest request) {
        var existing = deliveries.findByFirebaseUserIdAndDeliveryKey(firebaseUserId, request.deliveryKey()).orElse(null);
        if (existing != null && "SUBMITTED".equals(existing.status)) {
            return new AutomaticDeliveryResponse("SUBMITTED", existing.providerMessageId,
                "This reminder was already submitted", false, true);
        }

        final String recipient;
        try {
            recipient = normalizeRecipient(request.recipient());
        } catch (IllegalArgumentException exception) {
            return new AutomaticDeliveryResponse("FAILED", "", exception.getMessage(), false, false);
        }
        var template = templateFor(request.channel(), request.templateKey());
        if (template.isBlank() || !providerConfigured(request.channel())) {
            return new AutomaticDeliveryResponse("NOT_CONFIGURED", "",
                request.channel() + " provider or template is not configured", false, false);
        }

        var delivery = existing == null
            ? new CommunicationDelivery(firebaseUserId, request.deliveryKey(), request.channel(), recipient, request.templateKey())
            : existing;
        delivery.attemptCount++;
        delivery.updatedAt = Instant.now();
        delivery.status = "PENDING";
        delivery.errorDetail = null;
        deliveries.saveAndFlush(delivery);

        try {
            var providerId = "WHATSAPP".equals(request.channel())
                ? sendWhatsApp(recipient, template, request.parameters())
                : sendSms(recipient, template, request.parameters());
            delivery.status = "SUBMITTED";
            delivery.providerMessageId = providerId;
            delivery.updatedAt = Instant.now();
            deliveries.save(delivery);
            return new AutomaticDeliveryResponse("SUBMITTED", providerId,
                "Accepted by " + ("WHATSAPP".equals(request.channel()) ? "Meta" : "MSG91"), false, false);
        } catch (Exception exception) {
            delivery.status = "FAILED";
            delivery.errorDetail = safeDetail(exception);
            delivery.updatedAt = Instant.now();
            deliveries.save(delivery);
            return new AutomaticDeliveryResponse("FAILED", "", delivery.errorDetail, true, false);
        }
    }

    private boolean providerConfigured(String channel) {
        return "WHATSAPP".equals(channel) ? properties.whatsapp().configured() : properties.sms().configured();
    }

    private String templateFor(String channel, String key) {
        return "WHATSAPP".equals(channel)
            ? properties.whatsapp().templateFor(key)
            : properties.sms().templateFor(key);
    }

    @SuppressWarnings("unchecked")
    private String sendWhatsApp(String recipient, String templateName, List<String> parameters) {
        var values = parameters.stream()
            .map(value -> Map.of("type", "text", "text", value))
            .toList();
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", properties.whatsapp().languageCode()));
        if (!values.isEmpty()) template.put("components", List.of(Map.of("type", "body", "parameters", values)));
        var body = Map.of(
            "messaging_product", "whatsapp",
            "to", recipient,
            "type", "template",
            "template", template
        );
        Map<String, Object> response = restClient.post()
            .uri("https://graph.facebook.com/{version}/{phoneNumberId}/messages",
                properties.whatsapp().apiVersion(), properties.whatsapp().phoneNumberId())
            .headers(headers -> headers.setBearerAuth(properties.whatsapp().accessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);
        if (response != null && response.get("messages") instanceof List<?> messages && !messages.isEmpty()
            && messages.get(0) instanceof Map<?, ?> message && message.get("id") != null) {
            return message.get("id").toString();
        }
        throw new IllegalStateException("Meta did not return a message id");
    }

    @SuppressWarnings("unchecked")
    private String sendSms(String recipient, String templateId, List<String> parameters) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("mobiles", recipient);
        for (var index = 0; index < parameters.size(); index++) {
            target.put("VAR" + (index + 1), parameters.get(index));
        }
        var body = Map.of(
            "template_id", templateId,
            "short_url", "0",
            "realTimeResponse", "1",
            "recipients", List.of(target)
        );
        Map<String, Object> response = restClient.post()
            .uri("https://control.msg91.com/api/v5/flow")
            .header("authkey", properties.sms().authKey())
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);
        if (response != null && response.get("type") != null && "error".equalsIgnoreCase(response.get("type").toString())) {
            throw new IllegalStateException(String.valueOf(response.getOrDefault("message", "MSG91 rejected the message")));
        }
        if (response != null) {
            var id = response.get("request_id");
            if (id == null) id = response.get("requestId");
            if (id != null) return id.toString();
        }
        throw new IllegalStateException("MSG91 did not return a request id");
    }

    private String normalizeRecipient(String raw) {
        var trimmed = raw == null ? "" : raw.trim();
        var digits = trimmed.replaceAll("\\D", "");
        if (trimmed.startsWith("00") && digits.startsWith("00")) digits = digits.substring(2);
        if (!trimmed.startsWith("+") && digits.length() == 10) {
            digits = properties.sms().defaultCountryCallingCode() + digits;
        }
        if (!digits.matches("[1-9][0-9]{7,14}")) {
            throw new IllegalArgumentException("Recipient must be a valid international mobile number");
        }
        return digits;
    }

    private String safeDetail(Exception exception) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        return message.length() > 950 ? message.substring(0, 950) : message;
    }
}
