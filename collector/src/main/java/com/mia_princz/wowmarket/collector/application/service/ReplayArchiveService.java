package com.mia_princz.wowmarket.collector.application.service;

import com.mia_princz.wowmarket.collector.application.port.in.LoadSnapshotsUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.ReplayArchiveUseCase;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotReplayPort;
import com.mia_princz.wowmarket.collector.domain.pipeline.LoadSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.ReplaySummary;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Requeues every snapshot from a point in time onward and loads the queue until it is empty. Requeuing all sources
 * from that point, not a window in the middle, keeps the newest-loaded check meaningful: each snapshot is loaded again
 * after the ones before it. Loading replaces each snapshot's staged rows, so a replay can be repeated safely.
 */
public class ReplayArchiveService implements ReplayArchiveUseCase {

    private static final System.Logger LOG = System.getLogger(ReplayArchiveService.class.getName());

    /** A safety stop far beyond any real backlog: thirty days of half-hourly batches. */
    static final int MAX_BATCHES = 1_440;

    private final String region;
    private final int stagedDays;
    private final SnapshotReplayPort replay;
    private final LoadSnapshotsUseCase load;
    private final PipelineLock lock;
    private final Clock clock;

    public ReplayArchiveService(
            String region,
            int stagedDays,
            SnapshotReplayPort replay,
            LoadSnapshotsUseCase load,
            PipelineLock lock,
            Clock clock) {
        this.region = region;
        this.stagedDays = stagedDays;
        this.replay = replay;
        this.load = load;
        this.lock = lock;
        this.clock = clock;
    }

    @Override
    public ReplaySummary replayFrom(Instant from) {
        LocalDate firstStagedDay =
                LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(stagedDays);
        if (LocalDate.ofInstant(from, ZoneOffset.UTC).isBefore(firstStagedDay)) {
            throw new IllegalArgumentException(
                    "staging keeps days from " + firstStagedDay + "; earlier rows would be dropped again by retention");
        }
        return lock.exclusively(() -> {
            int requeued = replay.requeueFrom(region, from);
            LoadSummary total = new LoadSummary(0, 0, 0, 0);
            for (int batch = 0; batch < MAX_BATCHES; batch++) {
                LoadSummary loaded = load.loadPending();
                total = new LoadSummary(
                        total.loaded() + loaded.loaded(),
                        total.quarantined() + loaded.quarantined(),
                        total.failed() + loaded.failed(),
                        total.listingsStaged() + loaded.listingsStaged());
                // Stop when the queue is empty, or when only failing snapshots are left in it.
                if (loaded.loaded() + loaded.quarantined() == 0) {
                    break;
                }
            }
            ReplaySummary summary = new ReplaySummary(requeued, total);
            LOG.log(
                    Level.INFO,
                    () -> "replay from=" + from + " requeued=" + requeued + " loaded="
                            + summary.load().loaded() + " quarantined="
                            + summary.load().quarantined() + " failed="
                            + summary.load().failed());
            return summary;
        });
    }
}
