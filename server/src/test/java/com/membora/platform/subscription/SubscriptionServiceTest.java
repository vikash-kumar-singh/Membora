package com.membora.platform.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubscriptionServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    private static final String USER_ID = "firebase-user-1";
    private static final String TOKEN = "play-purchase-token";

    private AccountSubscriptionRepository repository;
    private PlayDeveloperApi play;
    private SubscriptionEntitlementPublisher publisher;
    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        repository = mock(AccountSubscriptionRepository.class);
        play = mock(PlayDeveloperApi.class);
        publisher = mock(SubscriptionEntitlementPublisher.class);
        when(repository.findByPurchaseToken(TOKEN)).thenReturn(Optional.empty());
        when(repository.findByFirebaseUserId(USER_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new SubscriptionService(
            repository,
            play,
            publisher,
            Clock.fixed(NOW, ZoneOffset.UTC),
            "membora_premium"
        );
    }

    @Test
    void verifiesAcknowledgesAndPublishesAnActiveSubscription() {
        when(play.getSubscription(TOKEN)).thenReturn(snapshot(
            USER_ID,
            "monthly",
            "SUBSCRIPTION_STATE_ACTIVE",
            "ACKNOWLEDGEMENT_STATE_PENDING",
            NOW.plusSeconds(30L * 24L * 60L * 60L)
        ));

        var response = service.verify(USER_ID, new VerifySubscriptionRequest("membora_premium", TOKEN));

        assertThat(response.entitled()).isTrue();
        assertThat(response.basePlanId()).isEqualTo("monthly");
        assertThat(response.autoRenewing()).isTrue();
        verify(play).acknowledgeSubscription("membora_premium", TOKEN);
        verify(publisher).publish(any(AccountSubscription.class), org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void rejectsPurchaseLinkedToAnotherFirebaseAccount() {
        when(play.getSubscription(TOKEN)).thenReturn(snapshot(
            "another-user",
            "annual",
            "SUBSCRIPTION_STATE_ACTIVE",
            "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
            NOW.plusSeconds(365L * 24L * 60L * 60L)
        ));

        assertThatThrownBy(() -> service.verify(
            USER_ID,
            new VerifySubscriptionRequest("membora_premium", TOKEN)
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("signed-in Membora account");
    }

    @Test
    void expiredSubscriptionDoesNotGrantAccess() {
        when(play.getSubscription(TOKEN)).thenReturn(snapshot(
            USER_ID,
            "six-month",
            "SUBSCRIPTION_STATE_EXPIRED",
            "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
            NOW.minusSeconds(1)
        ));

        var response = service.verify(USER_ID, new VerifySubscriptionRequest("membora_premium", TOKEN));

        assertThat(response.entitled()).isFalse();
        verify(publisher).publish(any(AccountSubscription.class), org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void playNotificationRefreshesAndRevokesAnExpiredSubscription() {
        var existing = existingSubscription();
        when(repository.findByPurchaseToken(TOKEN)).thenReturn(Optional.of(existing));
        when(play.getSubscription(TOKEN)).thenReturn(snapshot(
            USER_ID,
            "three-month",
            "SUBSCRIPTION_STATE_EXPIRED",
            "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
            NOW.minusSeconds(1)
        ));

        service.refreshFromPlayNotification(TOKEN);

        assertThat(existing.status).isEqualTo("SUBSCRIPTION_STATE_EXPIRED");
        verify(repository).save(existing);
        verify(publisher).publish(existing, false);
    }

    @Test
    void statusDoesNotUseCachedAccessForAnAccountValidationFailure() {
        var existing = existingSubscription();
        existing.status = "SUBSCRIPTION_STATE_ACTIVE";
        existing.expiresAt = NOW.plusSeconds(30L * 24L * 60L * 60L);
        existing.lastVerifiedAt = NOW.minusSeconds(60);
        when(repository.findByFirebaseUserId(USER_ID)).thenReturn(Optional.of(existing));
        when(play.getSubscription(TOKEN)).thenReturn(snapshot(
            "another-user",
            "monthly",
            "SUBSCRIPTION_STATE_ACTIVE",
            "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
            existing.expiresAt
        ));

        assertThatThrownBy(() -> service.status(USER_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("signed-in Membora account");
    }

    private AccountSubscription existingSubscription() {
        var value = new AccountSubscription(USER_ID, TOKEN);
        value.productId = "membora_premium";
        value.basePlanId = "monthly";
        value.lastVerifiedAt = NOW;
        return value;
    }

    private PlaySubscriptionSnapshot snapshot(
        String userId,
        String basePlan,
        String status,
        String acknowledgement,
        Instant expiresAt
    ) {
        return new PlaySubscriptionSnapshot(
            "membora_premium",
            basePlan,
            status,
            acknowledgement,
            SubscriptionService.obfuscatedAccountId(userId),
            "GPA.1234-5678",
            NOW.minusSeconds(100),
            expiresAt,
            true,
            false
        );
    }
}
