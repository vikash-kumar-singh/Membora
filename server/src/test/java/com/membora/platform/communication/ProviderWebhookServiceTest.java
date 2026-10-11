package com.membora.platform.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.membora.platform.communication.config.MessagingProviderConfig;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class ProviderWebhookServiceTest {
    private static final String META_SECRET = "meta-test-secret";

    @Test
    void verifiesMetaSubscriptionAndRecordsDeliveredWithoutRegressing() throws Exception {
        var repository = mock(CommunicationDeliveryRepository.class);
        var delivery = delivery("wamid.test");
        when(repository.findByProviderMessageId("wamid.test")).thenReturn(Optional.of(delivery));
        var service = service(repository);

        assertThat(service.verifyMetaSubscription("subscribe", "verify-me", "12345")).isEqualTo("12345");
        var delivered = metaPayload("delivered", "1791660000");
        var result = service.consumeMeta(signature(delivered), delivered);

        assertThat(result.updated()).isEqualTo(1);
        assertThat(delivery.status).isEqualTo("DELIVERED");
        assertThat(delivery.providerStatus).isEqualTo("DELIVERED");
        assertThat(delivery.deliveredAt).isNotNull();
        assertThat(delivery.webhookEventCount).isEqualTo(1);
        verify(repository).save(delivery);

        var olderSent = metaPayload("sent", "1791650000");
        service.consumeMeta(signature(olderSent), olderSent);
        assertThat(delivery.status).isEqualTo("DELIVERED");
        assertThat(delivery.providerStatus).isEqualTo("DELIVERED");
        assertThat(delivery.webhookEventCount).isEqualTo(2);
    }

    @Test
    void rejectsMetaPayloadWithInvalidSignature() {
        var service = service(mock(CommunicationDeliveryRepository.class));
        assertThatThrownBy(() -> service.consumeMeta("sha256=wrong", "{}"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("401");
    }

    @Test
    void doesNotReplaceAProviderFailureWithAnOlderSentEvent() throws Exception {
        var repository = mock(CommunicationDeliveryRepository.class);
        var delivery = delivery("wamid.test");
        when(repository.findByProviderMessageId("wamid.test")).thenReturn(Optional.of(delivery));
        var service = service(repository);

        var failed = metaPayload("failed", "1791660000");
        service.consumeMeta(signature(failed), failed);
        var olderSent = metaPayload("sent", "1791650000");
        service.consumeMeta(signature(olderSent), olderSent);

        assertThat(delivery.status).isEqualTo("FAILED");
        assertThat(delivery.providerStatus).isEqualTo("FAILED");
    }

    @Test
    void acceptsAuthenticatedMsg91FailureReport() {
        var repository = mock(CommunicationDeliveryRepository.class);
        var delivery = delivery("msg91-request-1");
        when(repository.findByProviderMessageId("msg91-request-1")).thenReturn(Optional.of(delivery));
        var service = service(repository);
        var body = """
            {"requestId":"msg91-request-1","status":"failed","description":"DLT rejected"}
            """;

        var result = service.consumeMsg91("msg91-callback-token", body, Map.of());

        assertThat(result.updated()).isEqualTo(1);
        assertThat(delivery.status).isEqualTo("FAILED");
        assertThat(delivery.errorDetail).isEqualTo("DLT rejected");
        assertThat(delivery.failedAt).isNotNull();
    }

    @Test
    void acceptsMsg91FlowFormPayloadAndNumericDeliveredStatus() {
        var repository = mock(CommunicationDeliveryRepository.class);
        var delivery = delivery("msg91-request-2");
        when(repository.findByProviderMessageId("msg91-request-2")).thenReturn(Optional.of(delivery));
        var service = service(repository);
        var data = """
            [{"requestId":"msg91-request-2","report":[{
              "date":"2026-10-11 14:30:00","status":"1","desc":"DELIVERED"
            }]}]
            """;

        var result = service.consumeMsg91("msg91-callback-token", "", Map.of("data", data));

        assertThat(result.updated()).isEqualTo(1);
        assertThat(delivery.status).isEqualTo("DELIVERED");
        assertThat(delivery.deliveredAt).isNotNull();
    }

    private ProviderWebhookService service(CommunicationDeliveryRepository repository) {
        var whatsapp = new MessagingProviderConfig.Properties.WhatsApp(
            "access", "phone", "v23.0", "en", "a", "b", "c", "d", "e", "f", "g"
        );
        var sms = new MessagingProviderConfig.Properties.Sms("auth", "91", "a", "b", "c", "d", "e", "f", "g");
        var webhooks = new MessagingProviderConfig.Properties.Webhooks(
            "verify-me", META_SECRET, "msg91-callback-token"
        );
        return new ProviderWebhookService(
            repository,
            new MessagingProviderConfig.Properties(whatsapp, sms, webhooks),
            new ObjectMapper()
        );
    }

    private CommunicationDelivery delivery(String providerMessageId) {
        var delivery = new CommunicationDelivery("user-1", "delivery-1", "WHATSAPP", "919999999999", "payment_due");
        delivery.status = "SUBMITTED";
        delivery.providerMessageId = providerMessageId;
        return delivery;
    }

    private String metaPayload(String status, String timestamp) {
        return """
            {"entry":[{"changes":[{"value":{"statuses":[{
              "id":"wamid.test","status":"%s","timestamp":"%s"
            }]}}]}]}
            """.formatted(status, timestamp);
    }

    private String signature(String body) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(META_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
