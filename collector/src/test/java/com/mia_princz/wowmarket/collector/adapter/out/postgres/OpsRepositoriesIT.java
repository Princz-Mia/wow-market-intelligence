package com.mia_princz.wowmarket.collector.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mia_princz.wowmarket.collector.TestcontainersConfiguration;
import com.mia_princz.wowmarket.collector.application.port.out.PipelineRunRepositoryPort;
import com.mia_princz.wowmarket.collector.application.port.out.RawArchivePort.ArchivedSnapshot;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotRegistryPort;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotRegistryPort.NewSnapshot;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.pipeline.CaptureSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.RunTrigger;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class OpsRepositoriesIT {

    private static final AuctionSource DRAENOR = new AuctionSource.ConnectedRealm(1403);
    private static final Instant T1 = Instant.parse("2026-11-07T16:21:54Z");
    private static final Instant T2 = Instant.parse("2026-11-07T17:21:54Z");

    @Autowired
    SnapshotRegistryPort registry;

    @Autowired
    PipelineRunRepositoryPort runs;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PostgreSQLContainer postgres;

    @BeforeEach
    void emptyTables() {
        jdbc.sql("TRUNCATE ops.snapshot, ops.pipeline_run RESTART IDENTITY").update();
    }

    private NewSnapshot snapshot(AuctionSource source, Instant lastModified, long runId) {
        return new NewSnapshot(
                "eu",
                source,
                lastModified,
                lastModified.plusSeconds(600),
                runId,
                new ArchivedSnapshot("eu/" + source.key() + "/x.json.gz", 1234, "a".repeat(64)));
    }

    @Test
    void registersEachSnapshotOnce() {
        long run = runs.start(RunTrigger.SCHEDULE, T2, 9);

        assertThat(registry.register(snapshot(DRAENOR, T2, run))).isTrue();
        assertThat(registry.register(snapshot(DRAENOR, T2, run))).isFalse();

        assertThat(jdbc.sql("SELECT count(*) FROM ops.snapshot")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void knowsTheNewestSnapshotPerSource() {
        long run = runs.start(RunTrigger.SCHEDULE, T2, 9);
        registry.register(snapshot(DRAENOR, T1, run));
        registry.register(snapshot(DRAENOR, T2, run));
        registry.register(snapshot(new AuctionSource.RegionCommodities(), T1, run));

        assertThat(registry.latestEventTime("eu", DRAENOR)).contains(T2);
        assertThat(registry.latestEventTime("eu", new AuctionSource.RegionCommodities()))
                .contains(T1);
        assertThat(registry.latestEventTime("eu", new AuctionSource.ConnectedRealm(1080)))
                .isEmpty();
        assertThat(registry.latestEventTime("us", DRAENOR)).isEmpty();
    }

    @Test
    void recordsARunFromStartToFinish() {
        long run = runs.start(RunTrigger.MANUAL, T2, 3);
        assertThat(status(run)).containsEntry("status", "RUNNING");

        runs.finish(run, T2.plusSeconds(42), new CaptureSummary(3, 1, 1, List.of("connected-realm-1080: HTTP 503")));

        assertThat(status(run))
                .containsEntry("status", "PARTIAL")
                .containsEntry("snapshots_new", 1)
                .containsEntry("sources_unchanged", 1)
                .containsEntry("sources_failed", 1)
                .containsEntry("error_summary", "connected-realm-1080: HTTP 503");
    }

    @Test
    void refusesASnapshotWithoutARun() {
        assertThatThrownBy(() -> registry.register(snapshot(DRAENOR, T2, 999))).hasMessageContaining("pipeline_run");
    }

    @Test
    void transformerCanReadTheRunLog() throws Exception {
        runs.start(RunTrigger.SCHEDULE, T2, 9);

        try (var transformer = DriverManager.getConnection(
                        TestcontainersConfiguration.warehouseUrl(postgres),
                        "transformer",
                        TestcontainersConfiguration.TRANSFORMER_PASSWORD);
                var result = transformer.createStatement().executeQuery("SELECT count(*) FROM ops.pipeline_run")) {
            result.next();
            assertThat(result.getInt(1)).isEqualTo(1);
        }
    }

    private Map<String, Object> status(long run) {
        return jdbc.sql("SELECT status, snapshots_new, sources_unchanged, sources_failed, error_summary"
                        + " FROM ops.pipeline_run WHERE id = ?")
                .param(run)
                .query()
                .singleRow();
    }
}
