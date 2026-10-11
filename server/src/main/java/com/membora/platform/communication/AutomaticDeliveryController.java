package com.membora.platform.communication;

import com.membora.platform.communication.dto.AutomaticDeliveryRequest;
import com.membora.platform.communication.dto.AutomaticDeliveryResponse;
import com.membora.platform.communication.dto.AutomaticDeliveryStatusResponse;
import com.membora.platform.communication.dto.MessagingConfigurationResponse;
import com.membora.platform.communication.config.MessagingProviderConfig;
import com.membora.platform.media.FirebaseIdentityVerifier;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/communications")
public class AutomaticDeliveryController {
    private final FirebaseIdentityVerifier identities;
    private final AutomaticDeliveryService deliveryService;
    private final MessagingProviderConfig.Properties properties;

    public AutomaticDeliveryController(
        FirebaseIdentityVerifier identities,
        AutomaticDeliveryService deliveryService,
        MessagingProviderConfig.Properties properties
    ) {
        this.identities = identities;
        this.deliveryService = deliveryService;
        this.properties = properties;
    }

    @PostMapping("/deliver")
    ResponseEntity<AutomaticDeliveryResponse> deliver(
        @RequestHeader(value = "X-Firebase-ID-Token", required = false) String firebaseIdToken,
        @Valid @RequestBody AutomaticDeliveryRequest request
    ) {
        var response = deliveryService.deliver(identities.requireUser(firebaseIdToken), request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping("/status")
    ResponseEntity<AutomaticDeliveryStatusResponse> status(
        @RequestHeader(value = "X-Firebase-ID-Token", required = false) String firebaseIdToken,
        @RequestParam("deliveryKey") String deliveryKey
    ) {
        var response = deliveryService.status(identities.requireUser(firebaseIdToken), deliveryKey);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping("/configuration")
    ResponseEntity<MessagingConfigurationResponse> configuration(
        @RequestHeader(value = "X-Firebase-ID-Token", required = false) String firebaseIdToken
    ) {
        identities.requireUser(firebaseIdToken);
        var templateKeys = List.of(
            "membership_expiring",
            "membership_expired",
            "payment_due",
            "birthday",
            "welcome",
            "payment_received",
            "renewal_thanks"
        );
        var response = new MessagingConfigurationResponse(
            properties.whatsapp().configured(),
            templateKeys.stream().filter(key -> properties.whatsapp().templateFor(key).isBlank()).toList(),
            properties.sms().configured(),
            templateKeys.stream().filter(key -> properties.sms().templateFor(key).isBlank()).toList(),
            properties.webhooks().metaConfigured(),
            properties.webhooks().msg91Configured()
        );
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
