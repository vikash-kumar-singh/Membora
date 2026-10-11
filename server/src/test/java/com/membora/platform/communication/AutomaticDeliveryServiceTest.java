package com.membora.platform.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.membora.platform.communication.config.MessagingProviderConfig;
import com.membora.platform.communication.dto.AutomaticDeliveryRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AutomaticDeliveryServiceTest {
    @Test
    void leavesMessageReadyWhenProviderIsNotConfigured() {
        var repository = mock(CommunicationDeliveryRepository.class);
        when(repository.findByFirebaseUserIdAndDeliveryKey("user-1", "delivery-1")).thenReturn(Optional.empty());
        var properties = new MessagingProviderConfig.Properties(null, null, null);
        var service = new AutomaticDeliveryService(repository, properties, RestClient.builder());

        var result = service.deliver("user-1", new AutomaticDeliveryRequest(
            "delivery-1", "WHATSAPP", "9876543210", "membership_expiring", List.of("A", "B", "C", "D")
        ));

        assertThat(result.status()).isEqualTo("NOT_CONFIGURED");
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void doesNotTreatExamplePlaceholdersAsConfiguredCredentials() {
        var repository = mock(CommunicationDeliveryRepository.class);
        when(repository.findByFirebaseUserIdAndDeliveryKey("user-1", "delivery-1")).thenReturn(Optional.empty());
        var whatsapp = new MessagingProviderConfig.Properties.WhatsApp(
            "replace-with-a-permanent-system-user-token",
            "replace-with-your-phone-number-id",
            "v23.0",
            "en",
            "membership_expiring",
            "membership_expired",
            "payment_due",
            "birthday",
            "welcome",
            "payment_received",
            "renewal_thanks"
        );
        var properties = new MessagingProviderConfig.Properties(whatsapp, null, null);
        var service = new AutomaticDeliveryService(repository, properties, RestClient.builder());

        var result = service.deliver("user-1", new AutomaticDeliveryRequest(
            "delivery-1", "WHATSAPP", "9876543210", "membership_expiring", List.of("A")
        ));

        assertThat(result.status()).isEqualTo("NOT_CONFIGURED");
    }

    @Test
    void submitsApprovedWhatsAppTemplateAndReturnsProviderMessageId() {
        var repository = mock(CommunicationDeliveryRepository.class);
        when(repository.findByFirebaseUserIdAndDeliveryKey("user-1", "delivery-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var whatsapp = new MessagingProviderConfig.Properties.WhatsApp(
            "secret-token", "12345", "v23.0", "en",
            "membership_expiring", "membership_expired", "payment_due", "birthday",
            "welcome", "payment_received", "renewal_thanks"
        );
        var sms = new MessagingProviderConfig.Properties.Sms("", "91", "", "", "", "", "", "", "");
        var properties = new MessagingProviderConfig.Properties(whatsapp, sms, null);
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://graph.facebook.com/v23.0/12345/messages"))
            .andExpect(header("Authorization", "Bearer secret-token"))
            .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.test\"}]}", MediaType.APPLICATION_JSON));
        var service = new AutomaticDeliveryService(repository, properties, builder);

        var result = service.deliver("user-1", new AutomaticDeliveryRequest(
            "delivery-1", "WHATSAPP", "9876543210", "membership_expiring", List.of("Rahul", "Annual", "2026-12-31", "Membora")
        ));

        assertThat(result.status()).isEqualTo("SUBMITTED");
        assertThat(result.providerMessageId()).isEqualTo("wamid.test");
        server.verify();
    }

    @Test
    void submitsMsg91FlowIdAndReturnsRequestId() {
        var repository = mock(CommunicationDeliveryRepository.class);
        when(repository.findByFirebaseUserIdAndDeliveryKey("user-1", "delivery-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var sms = new MessagingProviderConfig.Properties.Sms(
            "msg91-auth", "91", "flow-expiring", "flow-expired", "flow-due", "flow-birthday",
            "flow-welcome", "flow-payment", "flow-renewal"
        );
        var properties = new MessagingProviderConfig.Properties(null, sms, null);
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://control.msg91.com/api/v5/flow"))
            .andExpect(header("authkey", "msg91-auth"))
            .andExpect(content().json("""
                {"flow_id":"flow-due","short_url":"0","realTimeResponse":"1"}
                """, false))
            .andRespond(withSuccess("{\"request_id\":\"msg91-request\"}", MediaType.APPLICATION_JSON));
        var service = new AutomaticDeliveryService(repository, properties, builder);

        var result = service.deliver("user-1", new AutomaticDeliveryRequest(
            "delivery-1", "SMS", "9876543210", "payment_due", List.of("Rahul", "500")
        ));

        assertThat(result.status()).isEqualTo("SUBMITTED");
        assertThat(result.providerMessageId()).isEqualTo("msg91-request");
        server.verify();
    }

    @Test
    void rejectsInvalidMobileWithoutCallingProvider() {
        var repository = mock(CommunicationDeliveryRepository.class);
        when(repository.findByFirebaseUserIdAndDeliveryKey("user-1", "delivery-1")).thenReturn(Optional.empty());
        var properties = new MessagingProviderConfig.Properties(null, null, null);
        var service = new AutomaticDeliveryService(repository, properties, RestClient.builder());

        var result = service.deliver("user-1", new AutomaticDeliveryRequest(
            "delivery-1", "SMS", "not-a-number", "payment_due", List.of("A")
        ));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void neverResendsMessageAlreadyReportedDelivered() {
        var repository = mock(CommunicationDeliveryRepository.class);
        var existing = new CommunicationDelivery(
            "user-1", "delivery-1", "WHATSAPP", "919876543210", "payment_due"
        );
        existing.status = "DELIVERED";
        existing.providerMessageId = "wamid.delivered";
        when(repository.findByFirebaseUserIdAndDeliveryKey("user-1", "delivery-1"))
            .thenReturn(Optional.of(existing));
        var service = new AutomaticDeliveryService(
            repository, new MessagingProviderConfig.Properties(null, null, null), RestClient.builder()
        );

        var result = service.deliver("user-1", new AutomaticDeliveryRequest(
            "delivery-1", "WHATSAPP", "9876543210", "payment_due", List.of("A")
        ));

        assertThat(result.status()).isEqualTo("DELIVERED");
        assertThat(result.duplicate()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("wamid.delivered");
    }
}
