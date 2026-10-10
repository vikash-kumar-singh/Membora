package com.membora.platform.communication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AutomaticDeliveryRequest(
    @NotBlank @Size(max = 180) String deliveryKey,
    @NotBlank @Pattern(regexp = "WHATSAPP|SMS") String channel,
    @NotBlank @Size(max = 40) String recipient,
    @NotBlank @Pattern(regexp = "membership_expiring|membership_expired|payment_due|birthday|welcome|payment_received|renewal_thanks") String templateKey,
    @Size(max = 10) List<@NotBlank @Size(max = 256) String> parameters
) {
    public AutomaticDeliveryRequest {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }
}
