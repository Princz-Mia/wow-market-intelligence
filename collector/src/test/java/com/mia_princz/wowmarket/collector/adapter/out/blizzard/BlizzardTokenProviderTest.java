package com.mia_princz.wowmarket.collector.adapter.out.blizzard;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tools.jackson.databind.json.JsonMapper;

class BlizzardTokenProviderTest {

    @RegisterExtension
    static WireMockExtension oauth = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-07T12:00:00Z"));

    private BlizzardTokenProvider provider() {
        return new BlizzardTokenProvider(
                HttpClient.newHttpClient(),
                JsonMapper.builder().build(),
                clock,
                URI.create(oauth.baseUrl() + "/token"),
                "client-id",
                "client-secret",
                Duration.ofSeconds(5));
    }

    private static void tokenAnswers(String token, long expiresIn) {
        oauth.stubFor(post("/token")
                .withHeader("Authorization", equalTo("Basic Y2xpZW50LWlkOmNsaWVudC1zZWNyZXQ="))
                .withRequestBody(equalTo("grant_type=client_credentials"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"" + token + "\",\"token_type\":\"bearer\",\"expires_in\":"
                                + expiresIn + ",\"sub\":\"client-id\"}")));
    }

    @Test
    void requestsATokenWithClientCredentialsAndReusesIt() {
        tokenAnswers("first", 86399);
        BlizzardTokenProvider tokens = provider();

        assertThat(tokens.token()).isEqualTo("first");
        clock.advance(Duration.ofHours(23));
        assertThat(tokens.token()).isEqualTo("first");

        oauth.verify(exactly(1), postRequestedFor(urlEqualTo("/token")));
    }

    @Test
    void renewsShortlyBeforeExpiry() {
        tokenAnswers("first", 86399);
        BlizzardTokenProvider tokens = provider();
        tokens.token();

        tokenAnswers("second", 86399);
        clock.advance(Duration.ofSeconds(86399).minus(BlizzardTokenProvider.RENEW_BEFORE_EXPIRY));

        assertThat(tokens.token()).isEqualTo("second");
    }

    @Test
    void requestsANewTokenAfterInvalidation() {
        tokenAnswers("first", 86399);
        BlizzardTokenProvider tokens = provider();
        tokens.token();

        tokens.invalidate();
        tokens.token();

        oauth.verify(exactly(2), postRequestedFor(urlEqualTo("/token")));
    }

    @Test
    void reportsARefusedTokenRequestWithoutTheSecret() {
        oauth.stubFor(post("/token").willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> provider().token())
                .isInstanceOf(BlizzardApiException.class)
                .hasMessageContaining("HTTP 401")
                .hasMessageNotContaining("client-secret");
    }

    @Test
    void refusesAResponseWithoutToken() {
        oauth.stubFor(post("/token").willReturn(aResponse().withBody("{\"error\":\"invalid_client\"}")));

        assertThatThrownBy(() -> provider().token()).hasMessageContaining("no access_token");
    }
}
