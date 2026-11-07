package com.mia_princz.wowmarket.collector.adapter.out.blizzard;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.mia_princz.wowmarket.collector.application.port.out.FetchResult;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tools.jackson.databind.json.JsonMapper;

class BlizzardAuctionClientTest {

    // WireMock must not compress on its own: the stubs return bodies exactly as Blizzard sends them.
    @RegisterExtension
    static WireMockExtension api = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().gzipDisabled(true))
            .build();

    private static final String SNAPSHOT_JSON = "{\"auctions\":[{\"id\":1,\"item\":{\"id\":2},\"quantity\":3,"
            + "\"unit_price\":4,\"time_left\":\"SHORT\"}]}";
    private static final String LAST_MODIFIED = "Sat, 7 Nov 2026 17:21:54 GMT";

    private BlizzardAuctionClient client;

    @BeforeEach
    void setUp() {
        api.stubFor(post("/token").willReturn(aResponse().withBody("{\"access_token\":\"tok\",\"expires_in\":86399}")));
        HttpClient http = HttpClient.newHttpClient();
        BlizzardTokenProvider tokens = new BlizzardTokenProvider(
                http,
                JsonMapper.builder().build(),
                new MutableClock(Instant.parse("2026-11-07T12:00:00Z")),
                URI.create(api.baseUrl() + "/token"),
                "id",
                "secret",
                Duration.ofSeconds(5));
        client = new BlizzardAuctionClient(http, tokens, URI.create(api.baseUrl()), "eu", Duration.ofSeconds(5));
    }

    @Test
    void streamsANewCommoditySnapshotStillCompressed() throws IOException {
        api.stubFor(get("/data/wow/auctions/commodities")
                .withHeader("Authorization", equalTo("Bearer tok"))
                .withHeader("Battlenet-Namespace", equalTo("dynamic-eu"))
                .withHeader("Accept-Encoding", equalTo("gzip"))
                .withHeader("If-Modified-Since", absent())
                .willReturn(aResponse()
                        .withHeader("Last-Modified", LAST_MODIFIED)
                        .withHeader("Content-Encoding", "gzip")
                        .withBody(gzip(SNAPSHOT_JSON))));

        FetchResult result = client.fetch(new AuctionSource.RegionCommodities(), Optional.empty());

        assertThat(result).isInstanceOf(FetchResult.Modified.class);
        try (var modified = (FetchResult.Modified) result) {
            assertThat(modified.lastModified()).isEqualTo(Instant.parse("2026-11-07T17:21:54Z"));
            assertThat(modified.gzipped()).isTrue();
            assertThat(gunzip(modified.body().readAllBytes())).isEqualTo(SNAPSHOT_JSON);
        }
    }

    @Test
    void asksForConnectedRealmAuctionsOnTheirOwnPath() throws IOException {
        api.stubFor(get("/data/wow/connected-realm/1403/auctions")
                .willReturn(
                        aResponse().withHeader("Last-Modified", LAST_MODIFIED).withBody(SNAPSHOT_JSON)));

        try (var modified =
                (FetchResult.Modified) client.fetch(new AuctionSource.ConnectedRealm(1403), Optional.empty())) {
            assertThat(modified.gzipped()).isFalse();
            assertThat(new String(modified.body().readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo(SNAPSHOT_JSON);
        }
    }

    @Test
    void reportsAnUnchangedSnapshotFromAConditionalRequest() {
        api.stubFor(get("/data/wow/auctions/commodities")
                .withHeader("If-Modified-Since", equalTo(LAST_MODIFIED))
                .willReturn(aResponse().withStatus(304)));

        FetchResult result =
                client.fetch(new AuctionSource.RegionCommodities(), Optional.of(Instant.parse("2026-11-07T17:21:54Z")));

        assertThat(result).isInstanceOf(FetchResult.NotModified.class);
    }

    @Test
    void refusesASnapshotWithoutEventTime() {
        api.stubFor(get("/data/wow/auctions/commodities").willReturn(aResponse().withBody(SNAPSHOT_JSON)));

        assertThatThrownBy(() -> client.fetch(new AuctionSource.RegionCommodities(), Optional.empty()))
                .isInstanceOf(BlizzardApiException.class)
                .hasMessageContaining("without Last-Modified");
    }

    @Test
    void passesOnTheWaitBlizzardAsksForAfterTooManyRequests() {
        api.stubFor(get("/data/wow/auctions/commodities")
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "3")));

        assertThatThrownBy(() -> client.fetch(new AuctionSource.RegionCommodities(), Optional.empty()))
                .isInstanceOfSatisfying(BlizzardApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(429);
                    assertThat(e.retryAfter()).contains(Duration.ofSeconds(3));
                });
    }

    @Test
    void dropsTheTokenWhenTheApiRefusesIt() {
        api.stubFor(get("/data/wow/auctions/commodities").willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> client.fetch(new AuctionSource.RegionCommodities(), Optional.empty()))
                .isInstanceOfSatisfying(
                        BlizzardApiException.class, e -> assertThat(e.status()).isEqualTo(401));
        api.stubFor(get("/data/wow/auctions/commodities").willReturn(aResponse().withStatus(304)));
        client.fetch(new AuctionSource.RegionCommodities(), Optional.empty());

        api.verify(exactly(2), postRequestedFor(urlEqualTo("/token")));
    }

    private static byte[] gzip(String text) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new GZIPOutputStream(bytes)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    private static String gunzip(byte[] compressed) throws IOException {
        try (var in = new GZIPInputStream(new java.io.ByteArrayInputStream(compressed))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
