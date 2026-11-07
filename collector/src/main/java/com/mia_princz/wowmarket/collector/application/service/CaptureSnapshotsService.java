package com.mia_princz.wowmarket.collector.application.service;

import com.mia_princz.wowmarket.collector.application.port.in.CaptureSnapshotsUseCase;
import com.mia_princz.wowmarket.collector.application.port.out.AuctionSourcePort;
import com.mia_princz.wowmarket.collector.application.port.out.FetchResult;
import com.mia_princz.wowmarket.collector.application.port.out.PipelineRunRepositoryPort;
import com.mia_princz.wowmarket.collector.application.port.out.RawArchivePort;
import com.mia_princz.wowmarket.collector.application.port.out.RawArchivePort.ArchivedSnapshot;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotRegistryPort;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotRegistryPort.NewSnapshot;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.pipeline.CaptureSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.RunTrigger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One capture step: for each source, ask for the snapshot only if it changed since the newest one we have, then
 * archive and register it. Sources are fetched in parallel on virtual threads, at most {@code concurrency} at a time,
 * and a failing source never stops the others.
 */
public class CaptureSnapshotsService implements CaptureSnapshotsUseCase {

    private static final System.Logger LOG = System.getLogger(CaptureSnapshotsService.class.getName());

    private final String region;
    private final List<AuctionSource> sources;
    private final int concurrency;
    private final AuctionSourcePort auctionSource;
    private final RawArchivePort archive;
    private final SnapshotRegistryPort registry;
    private final PipelineRunRepositoryPort runs;
    private final Clock clock;
    private final ReentrantLock running = new ReentrantLock();

    public CaptureSnapshotsService(
            String region,
            List<AuctionSource> sources,
            int concurrency,
            AuctionSourcePort auctionSource,
            RawArchivePort archive,
            SnapshotRegistryPort registry,
            PipelineRunRepositoryPort runs,
            Clock clock) {
        this.region = region;
        this.sources = List.copyOf(sources);
        this.concurrency = concurrency;
        this.auctionSource = auctionSource;
        this.archive = archive;
        this.registry = registry;
        this.runs = runs;
        this.clock = clock;
    }

    @Override
    public CaptureSummary capture(RunTrigger trigger) {
        if (!running.tryLock()) {
            throw new CaptureAlreadyRunningException();
        }
        try {
            Instant startedAt = clock.instant();
            long runId = runs.start(trigger, startedAt, sources.size());
            CaptureSummary summary = summarize(captureAll(runId));
            Instant finishedAt = clock.instant();
            runs.finish(runId, finishedAt, summary);
            long durationMs = Duration.between(startedAt, finishedAt).toMillis();
            LOG.log(
                    summary.errors().isEmpty() ? Level.INFO : Level.WARNING,
                    () -> "capture run=" + runId + " trigger=" + trigger + " status=" + summary.status() + " new="
                            + summary.snapshotsNew() + " unchanged=" + summary.sourcesUnchanged() + " failed="
                            + summary.sourcesFailed() + " duration_ms=" + durationMs);
            return summary;
        } finally {
            running.unlock();
        }
    }

    private List<Outcome> captureAll(long runId) {
        Semaphore permits = new Semaphore(concurrency);
        List<Future<Outcome>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (AuctionSource source : sources) {
                futures.add(executor.submit(() -> {
                    permits.acquire();
                    try {
                        return captureOne(source, runId);
                    } finally {
                        permits.release();
                    }
                }));
            }
            List<Outcome> outcomes = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                outcomes.add(await(sources.get(i), futures.get(i)));
            }
            return outcomes;
        }
    }

    private Outcome captureOne(AuctionSource source, long runId) {
        Instant started = clock.instant();
        try {
            Optional<Instant> newest = registry.latestEventTime(region, source);
            FetchResult result = auctionSource.fetch(source, newest);
            Outcome outcome = switch (result) {
                case FetchResult.NotModified ignored -> Outcome.UNCHANGED;
                case FetchResult.Modified snapshot -> keep(source, runId, newest, snapshot);
            };
            long durationMs = Duration.between(started, clock.instant()).toMillis();
            LOG.log(
                    Level.INFO,
                    () -> "capture source=" + source.key() + " outcome=" + outcome.kind() + " duration_ms="
                            + durationMs);
            return outcome;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, () -> "capture source=" + source.key() + " outcome=FAILED error=" + e.getMessage());
            return Outcome.failed(source.key() + ": " + e.getMessage());
        }
    }

    private Outcome keep(AuctionSource source, long runId, Optional<Instant> newest, FetchResult.Modified snapshot) {
        try (snapshot) {
            if (newest.isPresent() && !snapshot.lastModified().isAfter(newest.get())) {
                // Served an older or equal snapshot despite If-Modified-Since; never let time go backwards.
                LOG.log(
                        Level.WARNING,
                        () -> "capture source=" + source.key() + " served last_modified=" + snapshot.lastModified()
                                + " not after newest=" + newest.get() + "; ignored");
                return Outcome.UNCHANGED;
            }
            ArchivedSnapshot archived =
                    archive.store(region, source, snapshot.lastModified(), snapshot.body(), snapshot.gzipped());
            boolean registered = registry.register(
                    new NewSnapshot(region, source, snapshot.lastModified(), clock.instant(), runId, archived));
            return registered ? Outcome.NEW : Outcome.UNCHANGED;
        } catch (IOException e) {
            throw new UncheckedIOException("could not close the response body", e);
        }
    }

    private static Outcome await(AuctionSource source, Future<Outcome> future) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            return Outcome.failed(source.key() + ": " + e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.failed(source.key() + ": interrupted");
        }
    }

    private CaptureSummary summarize(List<Outcome> outcomes) {
        int fresh = 0;
        int unchanged = 0;
        List<String> errors = new ArrayList<>();
        for (Outcome outcome : outcomes) {
            switch (outcome.kind()) {
                case NEW -> fresh++;
                case UNCHANGED -> unchanged++;
                case FAILED -> errors.add(outcome.error());
            }
        }
        return new CaptureSummary(sources.size(), fresh, unchanged, errors);
    }

    private record Outcome(Kind kind, String error) {
        static final Outcome NEW = new Outcome(Kind.NEW, null);
        static final Outcome UNCHANGED = new Outcome(Kind.UNCHANGED, null);

        static Outcome failed(String error) {
            return new Outcome(Kind.FAILED, error);
        }

        enum Kind {
            NEW,
            UNCHANGED,
            FAILED
        }
    }
}
