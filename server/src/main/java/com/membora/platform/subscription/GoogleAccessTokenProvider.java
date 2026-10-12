package com.membora.platform.subscription;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GoogleAccessTokenProvider {
    private static final List<String> SCOPES = List.of(
        "https://www.googleapis.com/auth/androidpublisher",
        "https://www.googleapis.com/auth/datastore"
    );

    private GoogleCredentials credentials;

    public synchronized String accessToken() {
        try {
            if (credentials == null) {
                credentials = GoogleCredentials.getApplicationDefault().createScoped(SCOPES);
            }
            credentials.refreshIfExpired();
            if (credentials.getAccessToken() == null) credentials.refreshAccessToken();
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Google service credentials are not configured for Play subscription verification",
                exception
            );
        }
    }
}
