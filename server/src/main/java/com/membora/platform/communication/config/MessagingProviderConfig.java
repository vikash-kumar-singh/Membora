package com.membora.platform.communication.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MessagingProviderConfig.Properties.class)
public class MessagingProviderConfig {
    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @ConfigurationProperties(prefix = "membora.messaging")
    public record Properties(WhatsApp whatsapp, Sms sms) {
        public Properties {
            whatsapp = whatsapp == null ? new WhatsApp("", "", "v23.0", "en", "", "", "", "", "", "", "") : whatsapp;
            sms = sms == null ? new Sms("", "91", "", "", "", "", "", "", "") : sms;
        }

        public record WhatsApp(
            String accessToken,
            String phoneNumberId,
            String apiVersion,
            String languageCode,
            String membershipExpiringTemplate,
            String membershipExpiredTemplate,
            String paymentDueTemplate,
            String birthdayTemplate,
            String welcomeTemplate,
            String paymentReceivedTemplate,
            String renewalThanksTemplate
        ) {
            public WhatsApp {
                accessToken = clean(accessToken);
                phoneNumberId = clean(phoneNumberId);
                apiVersion = clean(apiVersion).isBlank() ? "v23.0" : clean(apiVersion);
                languageCode = clean(languageCode).isBlank() ? "en" : clean(languageCode);
            }

            public boolean configured() {
                return !accessToken.isBlank() && !phoneNumberId.isBlank();
            }

            public String templateFor(String key) {
                return clean(switch (key) {
                    case "membership_expiring" -> membershipExpiringTemplate;
                    case "membership_expired" -> membershipExpiredTemplate;
                    case "payment_due" -> paymentDueTemplate;
                    case "birthday" -> birthdayTemplate;
                    case "welcome" -> welcomeTemplate;
                    case "payment_received" -> paymentReceivedTemplate;
                    case "renewal_thanks" -> renewalThanksTemplate;
                    default -> "";
                });
            }
        }

        public record Sms(
            String authKey,
            String defaultCountryCallingCode,
            String membershipExpiringTemplateId,
            String membershipExpiredTemplateId,
            String paymentDueTemplateId,
            String birthdayTemplateId,
            String welcomeTemplateId,
            String paymentReceivedTemplateId,
            String renewalThanksTemplateId
        ) {
            public Sms {
                authKey = clean(authKey);
                defaultCountryCallingCode = clean(defaultCountryCallingCode).isBlank() ? "91" : clean(defaultCountryCallingCode);
            }

            public boolean configured() { return !authKey.isBlank(); }

            public String templateFor(String key) {
                return clean(switch (key) {
                    case "membership_expiring" -> membershipExpiringTemplateId;
                    case "membership_expired" -> membershipExpiredTemplateId;
                    case "payment_due" -> paymentDueTemplateId;
                    case "birthday" -> birthdayTemplateId;
                    case "welcome" -> welcomeTemplateId;
                    case "payment_received" -> paymentReceivedTemplateId;
                    case "renewal_thanks" -> renewalThanksTemplateId;
                    default -> "";
                });
            }
        }

        private static String clean(String value) { return value == null ? "" : value.trim(); }
    }
}
