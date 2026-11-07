package com.mia_princz.wowmarket.collector.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mia_princz.wowmarket.collector.application.port.in.CaptureSnapshotsUseCase.CaptureAlreadyRunningException;
import com.mia_princz.wowmarket.collector.application.port.out.AuctionSourcePort;
import com.mia_princz.wowmarket.collector.application.port.out.FetchResult;
import com.mia_princz.wowmarket.collector.application.port.out.PipelineRunRepositoryPort;
import com.mia_princz.wowmarket.collector.application.port.out.RawArchivePort;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotRegistryPort;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.pipeline.CaptureSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.RunStatus;
import com.mia_princz.wowmarket.collector.domain.pipeline.RunTrigger;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class CaptureSnapshotsServiceTest {

    private static final AuctionSource COMMODITIES = new AuctionSource.RegionCommodities();
    private static final AuctionSource DRAENOR = new AuctionSource.ConnectedRealm(1403);
    private static final Instant OLD = Instant.parse("2026-11-07T16:21:54Z");
    private static final Instant NEW = Instant.parse("2026-11-07T17:21:54Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-07T17:40:00Z"), ZoneOffset.UTC);

    private final Map<AuctionSource, Instant> newest = new ConcurrentHashMap<>();
    private final List<SnapshotRegistryPort.NewSnapshot> registered = new ArrayList<>();
    private final List<String> archived = new ArrayList<>();
    private final Map<Long, CaptureSummary> finishedRuns = new HashMap<>();

    private final SnapshotRegistryPort registry = new SnapshotRegistryPort() {
        @Override
        public Optional<Instant> latestEventTime(String region, AuctionSource source) {
            return Optional.ofNullable(newest.get(source));
        }

        @Override
        public synchronized boolean register(NewSnapshot snapshot) {
            registered.add(snapshot);
            return true;
        }
    };

    private final RawArchivePort archive = (region, source, lastModified, body, gzipped) -> {
        try {
            body.readAllBytes();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        synchronized (archived) {
            archived.add(source.key() + "@" + lastModified);
        }
        return new RawArchivePort.ArchivedSnapshot(source.key() + ".json.gz", 10, "0".repeat(64));
    };

    private final PipelineRunRepositoryPort runs = new PipelineRunRepositoryPort() {
        @Override
        public long start(RunTrigger trigger, Instant startedAt, int sourcesTotal) {
            return 7;
        }

        @Override
        public void finish(long runId, Instant finishedAt, CaptureSummary summary) {
            finishedRuns.put(runId, summary);
        }
    };

    private CaptureSnapshotsService service(BiFunction<AuctionSource, Optional<Instant>, FetchResult> fetch) {
        AuctionSourcePort source = fetch::apply;
        return new CaptureSnapshotsService(
                "eu", List.of(COMMODITIES, DRAENOR), 2, source, archive, registry, runs, CLOCK);
    }

    private static FetchResult.Modified snapshot(Instant lastModified) {
        return new FetchResult.Modified(lastModified, new ByteArrayInputStream(new byte[] {1, 2, 3}), true);
    }

    @Test
    void archivesAndRegistersNewSnapshots() {
        CaptureSummary summary = service((source, since) -> snapshot(NEW)).capture(RunTrigger.SCHEDULE);

        assertThat(summary.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(summary.snapshotsNew()).isEqualTo(2);
        assertThat(archived).containsExactlyInAnyOrder("commodities@" + NEW, "connected-realm-1403@" + NEW);
        assertThat(registered).allSatisfy(snapshot -> {
            assertThat(snapshot.pipelineRunId()).isEqualTo(7);
            assertThat(snapshot.lastModified()).isEqualTo(NEW);
            assertThat(snapshot.fetchedAt()).isEqualTo(CLOCK.instant());
        });
        assertThat(finishedRuns).containsEntry(7L, summary);
    }

    @Test
    void asksOnlyForSnapshotsNewerThanTheOnesWeHave() {
        newest.put(DRAENOR, OLD);
        Map<AuctionSource, Optional<Instant>> asked = new ConcurrentHashMap<>();

        CaptureSummary summary = service((source, since) -> {
                    asked.put(source, since);
                    return source.equals(DRAENOR) ? new FetchResult.NotModified() : snapshot(NEW);
                })
                .capture(RunTrigger.SCHEDULE);

        assertThat(asked).containsEntry(DRAENOR, Optional.of(OLD)).containsEntry(COMMODITIES, Optional.empty());
        assertThat(summary.snapshotsNew()).isEqualTo(1);
        assertThat(summary.sourcesUnchanged()).isEqualTo(1);
    }

    @Test
    void ignoresASnapshotThatIsNotNewerAndClosesItsBody() {
        newest.put(COMMODITIES, NEW);
        newest.put(DRAENOR, NEW);
        List<Boolean> closed = new ArrayList<>();
        InputStream body = new ByteArrayInputStream(new byte[0]) {
            @Override
            public void close() {
                closed.add(true);
            }
        };

        CaptureSummary summary = service((source, since) -> source.equals(DRAENOR)
                        ? new FetchResult.Modified(OLD, body, true)
                        : new FetchResult.NotModified())
                .capture(RunTrigger.SCHEDULE);

        assertThat(summary.sourcesUnchanged()).isEqualTo(2);
        assertThat(archived).isEmpty();
        assertThat(closed).containsExactly(true);
    }

    @Test
    void keepsGoingWhenOneSourceFails() {
        CaptureSummary summary = service((source, since) -> {
                    if (source.equals(DRAENOR)) {
                        throw new IllegalStateException("HTTP 503");
                    }
                    return snapshot(NEW);
                })
                .capture(RunTrigger.SCHEDULE);

        assertThat(summary.status()).isEqualTo(RunStatus.PARTIAL);
        assertThat(summary.errors()).containsExactly("connected-realm-1403: HTTP 503");
        assertThat(archived).containsExactly("commodities@" + NEW);
        assertThat(finishedRuns.get(7L).status()).isEqualTo(RunStatus.PARTIAL);
    }

    @Test
    void refusesASecondCaptureWhileOneIsRunning() throws Exception {
        CountDownLatch fetching = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CaptureSnapshotsService service = service((source, since) -> {
            fetching.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new FetchResult.NotModified();
        });
        Thread first = Thread.ofVirtual().start(() -> service.capture(RunTrigger.SCHEDULE));
        assertThat(fetching.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> service.capture(RunTrigger.MANUAL)).isInstanceOf(CaptureAlreadyRunningException.class);

        release.countDown();
        first.join();
        assertThat(service.capture(RunTrigger.MANUAL).status()).isEqualTo(RunStatus.SUCCEEDED);
    }
}
