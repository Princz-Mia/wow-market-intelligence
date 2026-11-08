package com.mia_princz.wowmarket.collector.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.mia_princz.wowmarket.collector.TestcontainersConfiguration;
import com.mia_princz.wowmarket.collector.application.port.out.PipelineRunRepositoryPort;
import com.mia_princz.wowmarket.collector.application.port.out.PipelineStatusPort;
import com.mia_princz.wowmarket.collector.application.port.out.RawArchivePort.ArchivedSnapshot;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotLoadRegistryPort;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotLoadRegistryPort.LoadRecord;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotRegistryPort;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotRegistryPort.NewSnapshot;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.pipeline.CaptureSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.RunTrigger;
import com.mia_princz.wowmarket.collector.domain.quality.ValidationResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PipelineStatusIT {

    private static final Instant T1 = Instant.parse("2026-11-08T07:21:53Z");
    private static final Instant T2 = Instant.parse("2026-11-08T08:21:53Z");

    @Autowired
    PipelineStatusPort status;

    @Autowired
    PipelineRunRepositoryPort runs;

    @Autowired
    SnapshotRegistryPort snapshots;

    @Autowired
    SnapshotLoadRegistryPort loads;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        jdbc.sql("TRUNCATE ops.validation_result, ops.snapshot, ops.pipeline_run RESTART IDENTITY CASCADE")
                .update();
    }

    private long archive(long run, AuctionSource source, Instant lastModified) {
        snapshots.register(new NewSnapshot(
                "eu",
                source,
                lastModified,
                lastModified.plusSeconds(1100),
                run,
                new ArchivedSnapshot(source.key() + ".json.gz", 100, "a".repeat(64))));
        return jdbc.sql("SELECT max(id) FROM ops.snapshot").query(Long.class).single();
    }

    @Test
    void reportsNothingBeforeTheFirstRun() {
        assertThat(status.latestRun()).isEmpty();
        assertThat(status.lastCompletedRun()).isEmpty();
        assertThat(status.recentSnapshots("eu", Optional.empty(), 10)).isEmpty();
    }

    @Test
    void knowsTheLatestRunAndTheLastOneThatCompleted() {
        long first = runs.start(RunTrigger.SCHEDULE, T1, 9);
        runs.finish(first, T1.plusSeconds(3), new CaptureSummary(9, 9, 0, List.of()));
        long second = runs.start(RunTrigger.MANUAL, T2, 9);

        assertThat(status.latestRun()).hasValueSatisfying(run -> {
            assertThat(run.id()).isEqualTo(second);
            assertThat(run.trigger()).isEqualTo("MANUAL");
            assertThat(run.status()).isEqualTo("RUNNING");
            assertThat(run.finishedAt()).isNull();
        });
        assertThat(status.lastCompletedRun()).contains(T1.plusSeconds(3));
    }

    @Test
    void listsSnapshotsNewestFirstWithTheChecksThatDidNotPass() {
        long run = runs.start(RunTrigger.SCHEDULE, T1, 9);
        long older = archive(run, new AuctionSource.ConnectedRealm(1403), T1);
        long newer = archive(run, new AuctionSource.ConnectedRealm(1403), T2);
        archive(run, new AuctionSource.RegionCommodities(), T2);
        loads.recordLoad(new LoadRecord(
                newer,
                true,
                100L,
                100,
                50,
                T2.plusSeconds(1200),
                List.of(
                        new ValidationResult.Passed("DQ-I06", "no duplicate auction ids"),
                        new ValidationResult.Warned("DQ-I09", "1 of 100 listings flagged (1.000%)"))));

        assertThat(status.recentSnapshots("eu", Optional.of("connected-realm-1403"), 10))
                .satisfiesExactly(
                        latest -> {
                            assertThat(latest.id()).isEqualTo(newer);
                            assertThat(latest.status()).isEqualTo("LOADED");
                            assertThat(latest.listingsStaged()).isEqualTo(100);
                            assertThat(latest.checksNotPassed())
                                    .containsExactly("DQ-I09 WARNED: 1 of 100 listings flagged (1.000%)");
                        },
                        earlier -> {
                            assertThat(earlier.id()).isEqualTo(older);
                            assertThat(earlier.status()).isEqualTo("ARCHIVED");
                            assertThat(earlier.listingsRead()).isNull();
                            assertThat(earlier.checksNotPassed()).isEmpty();
                        });
        assertThat(status.recentSnapshots("eu", Optional.empty(), 2)).hasSize(2);
        assertThat(status.recentSnapshots("us", Optional.empty(), 10)).isEmpty();
    }
}
