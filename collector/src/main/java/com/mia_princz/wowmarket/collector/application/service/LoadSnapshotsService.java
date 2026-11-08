package com.mia_princz.wowmarket.collector.application.service;

import com.mia_princz.wowmarket.collector.application.port.in.LoadSnapshotsUseCase;
import com.mia_princz.wowmarket.collector.application.port.out.AuctionPayloadParserPort;
import com.mia_princz.wowmarket.collector.application.port.out.AuctionPayloadParserPort.MalformedPayloadException;
import com.mia_princz.wowmarket.collector.application.port.out.RawArchivePort;
import com.mia_princz.wowmarket.collector.application.port.out.RawArchivePort.MissingSnapshotFileException;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotLoadRegistryPort;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotLoadRegistryPort.LoadRecord;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotLoadRegistryPort.PendingSnapshot;
import com.mia_princz.wowmarket.collector.application.port.out.StagingWriterPort;
import com.mia_princz.wowmarket.collector.application.port.out.StagingWriterPort.StagingBatch;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.market.ItemVariantKey;
import com.mia_princz.wowmarket.collector.domain.pipeline.LoadSummary;
import com.mia_princz.wowmarket.collector.domain.quality.SnapshotValidator;
import com.mia_princz.wowmarket.collector.domain.quality.ValidationContext;
import com.mia_princz.wowmarket.collector.domain.quality.ValidationResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * The load step: each archived snapshot is read back from the raw zone, streamed through the ingestion checks into a
 * staging batch, and either committed or discarded depending on the verdict. Snapshots are loaded one at a time in
 * event-time order, so the newest-loaded check holds and a backlog after downtime is worked off oldest first.
 */
public class LoadSnapshotsService implements LoadSnapshotsUseCase {

    private static final System.Logger LOG = System.getLogger(LoadSnapshotsService.class.getName());

    private final String region;
    private final int batchSize;
    private final RawArchivePort archive;
    private final AuctionPayloadParserPort parser;
    private final StagingWriterPort staging;
    private final SnapshotLoadRegistryPort registry;
    private final Clock clock;
    private final ReentrantLock running = new ReentrantLock();

    public LoadSnapshotsService(
            String region,
            int batchSize,
            RawArchivePort archive,
            AuctionPayloadParserPort parser,
            StagingWriterPort staging,
            SnapshotLoadRegistryPort registry,
            Clock clock) {
        this.region = region;
        this.batchSize = batchSize;
        this.archive = archive;
        this.parser = parser;
        this.staging = staging;
        this.registry = registry;
        this.clock = clock;
    }

    @Override
    public LoadSummary loadPending() {
        if (!running.tryLock()) {
            throw new LoadAlreadyRunningException();
        }
        try {
            int loaded = 0;
            int quarantined = 0;
            int failed = 0;
            long staged = 0;
            // After a failure, later snapshots of that source wait, or they would overtake it and make it look stale.
            Set<AuctionSource> heldBack = new HashSet<>();
            for (PendingSnapshot snapshot : registry.pending(region, batchSize)) {
                if (heldBack.contains(snapshot.source())) {
                    continue;
                }
                try {
                    LoadRecord record = load(snapshot);
                    registry.recordLoad(record);
                    if (record.loaded()) {
                        loaded++;
                        staged += record.listingsStaged();
                    } else {
                        quarantined++;
                    }
                } catch (RuntimeException e) {
                    failed++;
                    heldBack.add(snapshot.source());
                    LOG.log(
                            Level.WARNING,
                            () -> "load snapshot=" + snapshot.id() + " source="
                                    + snapshot.source().key() + " status=FAILED error=" + e.getMessage(),
                            e);
                }
            }
            return new LoadSummary(loaded, quarantined, failed, staged);
        } finally {
            running.unlock();
        }
    }

    private LoadRecord load(PendingSnapshot snapshot) {
        Instant started = clock.instant();
        ValidationContext context = new ValidationContext(
                snapshot.source(),
                snapshot.lastModified(),
                registry.lastLoaded(region, snapshot.source()),
                registry.history(region, snapshot.source(), snapshot.lastModified()));
        SnapshotValidator validator = SnapshotValidator.forSnapshot(context);
        List<ValidationResult> results;
        long staged = 0;
        Long read = null;
        Integer distinctItems = null;
        try (InputStream json = archive.open(snapshot.archiveLocation());
                StagingBatch batch = staging.open(snapshot.id(), snapshot.source(), snapshot.lastModified())) {
            parser.parse(json, listing -> {
                if (validator.accept(listing)) {
                    batch.add(listing, ItemVariantKey.of(listing.item()));
                }
            });
            results = validator.finish();
            read = validator.listingsRead();
            distinctItems = validator.distinctItems();
            if (!SnapshotValidator.isBlocking(results)) {
                staged = batch.commit();
            }
        } catch (MalformedPayloadException | MissingSnapshotFileException e) {
            // Neither gets better by retrying; quarantine it so later snapshots of the source can load.
            results = SnapshotValidator.unreadable(e.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException("could not close " + snapshot.archiveLocation(), e);
        }
        boolean loaded = !SnapshotValidator.isBlocking(results);
        Instant finished = clock.instant();
        log(snapshot, loaded, read, staged, results, Duration.between(started, finished));
        return new LoadRecord(snapshot.id(), loaded, read, staged, distinctItems, finished, results);
    }

    private static void log(
            PendingSnapshot snapshot,
            boolean loaded,
            Long read,
            long staged,
            List<ValidationResult> results,
            Duration duration) {
        String notable = results.stream()
                .filter(result -> !(result instanceof ValidationResult.Passed))
                .map(result -> result.ruleId() + "=" + result.detail())
                .collect(Collectors.joining("; "));
        LOG.log(
                loaded ? Level.INFO : Level.WARNING,
                () -> "load snapshot=" + snapshot.id() + " source="
                        + snapshot.source().key() + " event_time="
                        + snapshot.lastModified() + " status=" + (loaded ? "LOADED" : "QUARANTINED") + " read="
                        + read + " staged=" + staged + " duration_ms=" + duration.toMillis()
                        + (notable.isEmpty() ? "" : " checks=[" + notable + "]"));
    }
}
