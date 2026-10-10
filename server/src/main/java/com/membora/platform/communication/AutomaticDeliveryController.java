package com.membora.platform.communication;

import com.membora.platform.communication.dto.AutomaticDeliveryRequest;
import com.membora.platform.communication.dto.AutomaticDeliveryResponse;
import com.membora.platform.media.FirebaseIdentityVerifier;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/communications")
public class AutomaticDeliveryController {
    private final FirebaseIdentityVerifier identities;
    private final AutomaticDeliveryService deliveryService;

    public AutomaticDeliveryController(FirebaseIdentityVerifier identities, AutomaticDeliveryService deliveryService) {
        this.identities = identities;
        this.deliveryService = deliveryService;
    }

    @PostMapping("/deliver")
    ResponseEntity<AutomaticDeliveryResponse> deliver(
        @RequestHeader(value = "X-Firebase-ID-Token", required = false) String firebaseIdToken,
        @Valid @RequestBody AutomaticDeliveryRequest request
    ) {
        var response = deliveryService.deliver(identities.requireUser(firebaseIdToken), request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
