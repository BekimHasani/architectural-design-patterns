package com.designpatterns.level09.integration.provider.paypal;

import com.designpatterns.level09.integration.gateway.ProviderCredentials;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** One OAuth2 token PER MERCHANT'S CLIENT ID, fetched once and reused until shortly before it expires. */
final class PayPalAccessTokens {

    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(1);

    private record Token(String value, Instant expiresAt) {
    }

    private final RestClient paypal;
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();

    PayPalAccessTokens(RestClient paypal) {
        this.paypal = paypal;
    }

    String current(ProviderCredentials credentials) {
        String clientId = credentials.require("client-id");
        return tokens.compute(clientId, (id, token) -> {
            if (token != null && Instant.now().isBefore(token.expiresAt().minus(REFRESH_MARGIN))) {
                return token;
            }
            PayPalApi.AccessToken fresh = paypal.post()
                    .uri("/v1/oauth2/token")
                    .headers(h -> h.setBasicAuth(id, credentials.require("client-secret")))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=client_credentials")
                    .retrieve() // a 401 throws RestClientResponseException → mapped to INVALID_REQUEST
                    .body(PayPalApi.AccessToken.class);
            return new Token(fresh.accessToken(), Instant.now().plusSeconds(fresh.expiresIn()));
        }).value();
    }
}
