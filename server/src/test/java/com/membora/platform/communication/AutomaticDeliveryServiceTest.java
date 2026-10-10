package com.membora.platform.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
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
        var properties = new MessagingProviderConfig.Properties(null, null);
        var service = new AutomaticDeliveryService(repository, properties, RestClient.builder());

        var result = service.deliver("user-1", new AutomaticDeliveryRequest(
            "delivery-1", "WHATSAPP", "9876543210", "membership_expiring", List.of("A", "B", "C", "D")
        ));

        assertThat(result.status()).isEqualTo("NOT_CONFIGURED");
        assertThat(result.retryable()).isFalse();
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
        var properties = new MessagingProviderConfig.Properties(whatsapp, sms);
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
    void rejectsInvalidMobileWithoutCallingProvider() {
        var repository = mock(CommunicationDeliveryRepository.class);
        when(repository.findByFirebaseUserIdAndDeliveryKey("user-1", "delivery-1")).thenReturn(Optional.empty());
        var properties = new MessagingProviderConfig.Properties(null, null);
        var service = new AutomaticDeliveryService(repository, properties, RestClient.builder());

        var result = service.deliver("user-1", new AutomaticDeliveryRequest(
            "delivery-1", "SMS", "not-a-number", "payment_due", List.of("A")
        ));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.retryable()).isFalse();
    }
}
