package com.membora.platform.communication.dto;

public record AutomaticDeliveryResponse(
    String status,
    String providerMessageId,
    String detail,
    boolean retryable,
    boolean duplicate
) {}
