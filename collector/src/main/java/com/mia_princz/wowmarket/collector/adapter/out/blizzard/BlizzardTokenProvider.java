package com.mia_princz.wowmarket.collector.adapter.out.blizzard;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Obtains an access token with the OAuth client credentials flow and reuses it until shortly before it expires.
 * Tokens live 24 hours, so one run normally needs no token request at all.
 */
public class BlizzardTokenProvider {

    /** Renew this long before expiry, so a token never runs out in the middle of a run. */
    static final Duration RENEW_BEFORE_EXPIRY = Duration.ofMinutes(5);

    private final HttpClient http;
    private final JsonMapper json;
    private final Clock clock;
    private final URI tokenUrl;
    private final String basicCredentials;
    private final Duration requestTimeout;

    private String token;
    private Instant renewAt = Instant.MIN;

    public BlizzardTokenProvider(
            HttpClient http,
            JsonMapper json,
            Clock clock,
            URI tokenUrl,
            String clientId,
            String clientSecret,
            Duration requestTimeout) {
        this.http = http;
        this.json = json;
        this.clock = clock;
        this.tokenUrl = tokenUrl;
        this.basicCredentials =
                Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        this.requestTimeout = requestTimeout;
    }

    public synchronized String token() {
        if (token == null || !clock.instant().isBefore(renewAt)) {
            requestToken();
        }
        return token;
    }

    /** Forgets the cached token, after the API refused it. */
    public synchronized void invalidate() {
        token = null;
    }

    private void requestToken() {
        HttpRequest request = HttpRequest.newBuilder(tokenUrl)
                .timeout(requestTimeout)
                .header("Authorization", "Basic " + basicCredentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new BlizzardApiException("token request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BlizzardApiException("token request interrupted", e);
        }
        if (response.statusCode() != 200) {
            throw new BlizzardApiException(
                    "token request answered HTTP " + response.statusCode(), response.statusCode(), null);
        }
        JsonNode body = json.readTree(response.body());
        String accessToken = body.path("access_token").asString("");
        long expiresIn = body.path("expires_in").asLong(0);
        if (accessToken.isBlank() || expiresIn <= 0) {
            throw new BlizzardApiException("token response has no access_token or expires_in", 200, null);
        }
        token = accessToken;
        renewAt = clock.instant().plusSeconds(expiresIn).minus(RENEW_BEFORE_EXPIRY);
    }
}
