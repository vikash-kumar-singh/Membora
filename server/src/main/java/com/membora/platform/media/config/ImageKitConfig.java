package com.membora.platform.media.config;

import io.imagekit.client.ImageKitClient;
import io.imagekit.client.okhttp.ImageKitOkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the single server-side ImageKit client. The private key never leaves this process. */
@Configuration
@EnableConfigurationProperties(ImageKitConfig.Properties.class)
public class ImageKitConfig {
    @Bean
    ImageKitClient imageKitClient(Properties properties) {
        return ImageKitOkHttpClient.builder()
            .privateKey(properties.requiredPrivateKey())
            .build();
    }

    @ConfigurationProperties(prefix = "membora.imagekit")
    public record Properties(String privateKey, String publicKey, String urlEndpoint) {
        public Properties {
            privateKey = privateKey == null ? "" : privateKey.trim();
            publicKey = publicKey == null ? "" : publicKey.trim();
            urlEndpoint = urlEndpoint == null ? "" : urlEndpoint.trim().replaceAll("/+$", "");
        }

        public String requiredPrivateKey() {
            if (privateKey.isBlank()) {
                throw new IllegalStateException("IMAGEKIT_PRIVATE_KEY is not configured on the server");
            }
            return privateKey;
        }

        public String requiredPublicKey() {
            if (publicKey.isBlank()) {
                throw new IllegalStateException("IMAGEKIT_PUBLIC_KEY is not configured on the server");
            }
            return publicKey;
        }

        public String requiredUrlEndpoint() {
            if (!urlEndpoint.startsWith("https://")) {
                throw new IllegalStateException("IMAGEKIT_URL_ENDPOINT must use HTTPS");
            }
            return urlEndpoint;
        }
    }
}
