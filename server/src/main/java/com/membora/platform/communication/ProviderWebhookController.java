package com.membora.platform.communication;

import com.membora.platform.communication.dto.WebhookResult;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
public class ProviderWebhookController {
    private final ProviderWebhookService webhooks;

    public ProviderWebhookController(ProviderWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @GetMapping(value = "/meta-whatsapp", produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> verifyMeta(
        @RequestParam(name = "hub.mode", required = false) String mode,
        @RequestParam(name = "hub.verify_token", required = false) String token,
        @RequestParam(name = "hub.challenge", required = false) String challenge
    ) {
        return ResponseEntity.ok(webhooks.verifyMetaSubscription(mode, token, challenge));
    }

    @PostMapping("/meta-whatsapp")
    ResponseEntity<WebhookResult> metaEvent(
        @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
        @RequestBody(required = false) String body
    ) {
        return ResponseEntity.ok(webhooks.consumeMeta(signature, body));
    }

    @PostMapping("/msg91-sms")
    ResponseEntity<WebhookResult> msg91Event(
        @RequestHeader(name = "X-Membora-Webhook-Token", required = false) String headerToken,
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @RequestParam(name = "token", required = false) String queryToken,
        @RequestParam Map<String, String> parameters,
        @RequestBody(required = false) String body
    ) {
        var bearer = authorization != null && authorization.startsWith("Bearer ")
            ? authorization.substring("Bearer ".length()).trim() : null;
        var suppliedToken = headerToken != null ? headerToken : bearer != null ? bearer : queryToken;
        return ResponseEntity.ok(webhooks.consumeMsg91(suppliedToken, body, parameters));
    }
}
