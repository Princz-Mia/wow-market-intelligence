package com.mia_princz.wowmarket.collector.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.mia_princz.wowmarket.collector.TestcontainersConfiguration;
import com.mia_princz.wowmarket.collector.application.port.out.PipelineRunRepositoryPort;
import com.mia_princz.wowmarket.collector.application.port.out.RawArchivePort.ArchivedSnapshot;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotLoadRegistryPort;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotLoadRegistryPort.LoadRecord;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotLoadRegistryPort.PendingSnapshot;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotRegistryPort;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotRegistryPort.NewSnapshot;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotReplayPort;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.pipeline.RunTrigger;
import com.mia_princz.wowmarket.collector.domain.quality.ValidationContext.History;
import com.mia_princz.wowmarket.collector.domain.quality.ValidationResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SnapshotLoadRegistryIT {

    private static final AuctionSource DRAENOR = new AuctionSource.ConnectedRealm(1403);
    private static final AuctionSource COMMODITIES = new AuctionSource.RegionCommodities();
    private static final Instant T0 = Instant.parse("2026-11-07T10:21:53Z");

    @Autowired
    SnapshotRegistryPort snapshots;

    @Autowired
    SnapshotLoadRegistryPort loads;

    @Autowired
    SnapshotReplayPort replays;

    @Autowired
    PipelineRunRepositoryPort runs;

    @Autowired
    JdbcClient jdbc;

    private long run;

    @BeforeEach
    void emptyTables() {
        jdbc.sql("TRUNCATE ops.validation_result, ops.snapshot, ops.pipeline_run RESTART IDENTITY CASCADE")
                .update();
        run = runs.start(RunTrigger.SCHEDULE, T0, 9);
    }

    private long archive(AuctionSource source, Instant lastModified) {
        snapshots.register(new NewSnapshot(
                "eu",
                source,
                lastModified,
                lastModified.plusSeconds(600),
                run,
                new ArchivedSnapshot(source.key() + "/" + lastModified + ".json.gz", 100, "a".repeat(64))));
        return jdbc.sql("SELECT id FROM ops.snapshot WHERE source = ? AND last_modified = ?")
                .params(source.key(), java.sql.Timestamp.from(lastModified))
                .query(Long.class)
                .single();
    }

    private void record(long id, boolean loaded, long read, int items, ValidationResult... results) {
        loads.recordLoad(
                new LoadRecord(id, loaded, read, loaded ? read : 0, items, T0.plusSeconds(60), List.of(results)));
    }

    @Test
    void queuesArchivedSnapshotsOldestFirst() {
        long later = archive(DRAENOR, T0.plusSeconds(3600));
        long earlier = archive(COMMODITIES, T0);
        long processed = archive(DRAENOR, T0.minusSeconds(3600));
        record(processed, true, 10, 5);

        assertThat(loads.pending("eu", 10)).extracting(PendingSnapshot::id).containsExactly(earlier, later);
        assertThat(loads.pending("eu", 1)).singleElement().satisfies(pending -> {
            assertThat(pending.source()).isEqualTo(COMMODITIES);
            assertThat(pending.lastModified()).isEqualTo(T0);
            assertThat(pending.archiveLocation()).isEqualTo("commodities/" + T0 + ".json.gz");
        });
        assertThat(loads.pending("us", 10)).isEmpty();
    }

    @Test
    void knowsTheNewestLoadedSnapshotButNotQuarantinedOnes() {
        record(archive(DRAENOR, T0), true, 10, 5);
        record(archive(DRAENOR, T0.plusSeconds(3600)), false, 10, 5);
        archive(DRAENOR, T0.plusSeconds(7200));

        assertThat(loads.lastLoaded("eu", DRAENOR)).contains(T0);
        assertThat(loads.lastLoaded("eu", COMMODITIES)).isEmpty();
    }

    @Test
    void takesMediansOverTheSevenDaysBeforeASnapshot() {
        Instant now = T0.plus(Duration.ofDays(8));
        record(archive(DRAENOR, now.minus(Duration.ofDays(7)).minusSeconds(1)), true, 1_000_000, 1);
        record(archive(DRAENOR, now.minus(Duration.ofDays(7))), true, 100, 10);
        record(archive(DRAENOR, now.minusSeconds(7200)), false, 300, 30);
        record(archive(DRAENOR, now.minusSeconds(3600)), true, 200, 20);
        record(archive(DRAENOR, now), true, 1_000_000, 1);
        loads.recordLoad(new LoadRecord(
                archive(DRAENOR, now.minusSeconds(1800)),
                false,
                null,
                0,
                null,
                now,
                List.of(new ValidationResult.Failed("DQ-I01", "not JSON"))));

        assertThat(loads.history("eu", DRAENOR, now)).isEqualTo(new History(3, 200, 20));
        assertThat(loads.history("eu", COMMODITIES, now)).isEqualTo(History.NONE);
    }

    @Test
    void requeuesProcessedSnapshotsFromAPointInTime() {
        long before = archive(DRAENOR, T0.minusSeconds(3600));
        long loaded = archive(DRAENOR, T0);
        long quarantined = archive(COMMODITIES, T0.plusSeconds(3600));
        long waiting = archive(DRAENOR, T0.plusSeconds(7200));
        record(before, true, 10, 5);
        record(loaded, true, 10, 5, new ValidationResult.Passed("DQ-I06", "no duplicate auction ids"));
        record(quarantined, false, 10, 5, new ValidationResult.Failed("DQ-I06", "1 duplicate auction ids"));

        assertThat(replays.requeueFrom("eu", T0)).isEqualTo(2);

        assertThat(loads.pending("eu", 10))
                .extracting(PendingSnapshot::id)
                .containsExactly(loaded, quarantined, waiting);
        assertThat(jdbc.sql("SELECT count(*) FROM ops.validation_result")
                        .query(Long.class)
                        .single())
                .isZero();
        assertThat(jdbc.sql("SELECT listings_read FROM ops.snapshot WHERE id = ?")
                        .param(loaded)
                        .query(Integer.class)
                        .optional())
                .isEmpty();
        assertThat(loads.lastLoaded("eu", DRAENOR)).contains(T0.minusSeconds(3600));
    }

    @Test
    void recordsAVerdictAndReplacesItOnReload() {
        long id = archive(DRAENOR, T0);
        record(id, false, 10, 5, new ValidationResult.Failed("DQ-I06", "1 duplicate auction ids"));

        record(
                id,
                true,
                10,
                5,
                new ValidationResult.Passed("DQ-I06", "no duplicate auction ids"),
                new ValidationResult.Warned("DQ-I09", "1 of 10 listings flagged (10.000%)"));

        assertThat(jdbc.sql("SELECT status, listings_read, listings_staged, distinct_items FROM ops.snapshot")
                        .query()
                        .singleRow())
                .containsEntry("status", "LOADED")
                .containsEntry("listings_read", 10)
                .containsEntry("listings_staged", 10)
                .containsEntry("distinct_items", 5);
        assertThat(jdbc.sql("SELECT rule_id || ':' || outcome FROM ops.validation_result ORDER BY rule_id")
                        .query(String.class)
                        .list())
                .containsExactly("DQ-I06:PASSED", "DQ-I09:WARNED");
    }
}
