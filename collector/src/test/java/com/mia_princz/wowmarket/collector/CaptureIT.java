package com.mia_princz.wowmarket.collector;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.mia_princz.wowmarket.collector.application.port.in.CaptureSnapshotsUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.LoadSnapshotsUseCase;
import com.mia_princz.wowmarket.collector.domain.pipeline.CaptureSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.LoadSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.RunStatus;
import com.mia_princz.wowmarket.collector.domain.pipeline.RunTrigger;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Capture and load against a fake Blizzard API, writing to a real archive folder and PostgreSQL. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"collector.connected-realms=1403,1080", "collector.blizzard.max-attempts=1"})
class CaptureIT {

    private static final String LAST_MODIFIED = "Sat, 7 Nov 2026 17:21:54 GMT";

    @RegisterExtension
    static WireMockExtension blizzard = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().gzipDisabled(true))
            .build();

    @TempDir
    static Path archiveRoot;

    @DynamicPropertySource
    static void blizzardAndArchive(DynamicPropertyRegistry registry) {
        registry.add("collector.blizzard.token-url", () -> blizzard.baseUrl() + "/token");
        registry.add("collector.blizzard.api-url", blizzard::baseUrl);
        registry.add("collector.archive.root", archiveRoot::toString);
    }

    @Autowired
    CaptureSnapshotsUseCase capture;

    @Autowired
    LoadSnapshotsUseCase load;

    @Autowired
    JdbcClient jdbc;

    @Test
    void capturesAndLoadsNewSnapshotsOnceThenSeesThemUnchanged() throws IOException {
        blizzard.stubFor(
                post("/token").willReturn(aResponse().withBody("{\"access_token\":\"tok\",\"expires_in\":86399}")));
        blizzard.stubFor(get(urlPathMatching("/data/wow/.*"))
                .withHeader("If-Modified-Since", equalTo(LAST_MODIFIED))
                .atPriority(1)
                .willReturn(aResponse().withStatus(304)));
        blizzard.stubFor(get(urlPathMatching("/data/wow/.*"))
                .atPriority(2)
                .willReturn(aResponse()
                        .withHeader("Last-Modified", LAST_MODIFIED)
                        .withHeader("Content-Encoding", "gzip")
                        .withBody(gzip("{\"auctions\":[]}"))));
        blizzard.stubFor(get("/data/wow/connected-realm/1080/auctions")
                .atPriority(0)
                .willReturn(aResponse().withStatus(503)));
        blizzard.stubFor(get("/data/wow/auctions/commodities").atPriority(2).willReturn(fixture("commodities.json")));
        blizzard.stubFor(get("/data/wow/connected-realm/1403/auctions")
                .atPriority(2)
                .willReturn(fixture("connected-realm-1403.json")));

        CaptureSummary first = capture.capture(RunTrigger.MANUAL);

        assertThat(first.status()).isEqualTo(RunStatus.PARTIAL);
        assertThat(first.snapshotsNew()).isEqualTo(2);
        assertThat(first.errors()).singleElement().asString().startsWith("connected-realm-1080: ");
        assertThat(Files.exists(archiveRoot.resolve("eu/commodities/2026/11/07/17/20261107T172154Z.json.gz")))
                .isTrue();
        assertThat(Files.exists(archiveRoot.resolve("eu/connected-realm-1403/2026/11/07/17/20261107T172154Z.json.gz")))
                .isTrue();

        LoadSummary loaded = load.loadPending();

        assertThat(loaded).isEqualTo(new LoadSummary(2, 0, 0, 6));
        assertThat(jdbc.sql("SELECT count(*) FROM raw.auction_listing")
                        .query(Long.class)
                        .single())
                .isEqualTo(6);
        assertThat(jdbc.sql(
                                "SELECT source || ':' || status || ':' || listings_staged FROM ops.snapshot ORDER BY source")
                        .query(String.class)
                        .list())
                .containsExactly("commodities:LOADED:3", "connected-realm-1403:LOADED:3");
        assertThat(jdbc.sql("SELECT count(*) FROM ops.validation_result WHERE outcome = 'PASSED'")
                        .query(Long.class)
                        .single())
                .isEqualTo(16);
        assertThat(load.loadPending()).isEqualTo(new LoadSummary(0, 0, 0, 0));

        CaptureSummary second = capture.capture(RunTrigger.MANUAL);

        assertThat(second.snapshotsNew()).isZero();
        assertThat(second.sourcesUnchanged()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM ops.snapshot")
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
        assertThat(jdbc.sql("SELECT string_agg(status, ',' ORDER BY id) FROM ops.pipeline_run")
                        .query(String.class)
                        .single())
                .isEqualTo("PARTIAL,PARTIAL");
    }

    private static ResponseDefinitionBuilder fixture(String name) throws IOException {
        try (InputStream json = CaptureIT.class.getResourceAsStream("/fixtures/" + name)) {
            return aResponse()
                    .withHeader("Last-Modified", LAST_MODIFIED)
                    .withHeader("Content-Encoding", "gzip")
                    .withBody(gzip(new String(json.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    private static byte[] gzip(String text) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new GZIPOutputStream(bytes)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }
}
