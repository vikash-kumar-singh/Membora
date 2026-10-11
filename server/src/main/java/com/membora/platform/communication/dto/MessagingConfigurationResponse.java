package com.membora.platform.communication.dto;

import java.util.List;

public record MessagingConfigurationResponse(
    boolean whatsappCredentialsConfigured,
    List<String> missingWhatsAppTemplates,
    boolean smsCredentialsConfigured,
    List<String> missingSmsTemplates,
    boolean metaDeliveryWebhookConfigured,
    boolean msg91DeliveryWebhookConfigured
) {}
