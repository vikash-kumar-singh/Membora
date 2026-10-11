package com.membora.platform.communication.dto;

import java.time.Instant;

public record AutomaticDeliveryStatusResponse(
    String status,
    String providerMessageId,
    String providerStatus,
    String detail,
    Instant updatedAt
) {}
