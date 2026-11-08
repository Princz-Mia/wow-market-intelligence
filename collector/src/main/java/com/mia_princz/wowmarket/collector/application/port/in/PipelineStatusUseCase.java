package com.mia_princz.wowmarket.collector.application.port.in;

import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** What the pipeline has done lately, for operators and the health check. */
public interface PipelineStatusUseCase {

    Optional<RunView> latestRun();

    /** The newest snapshots first, of one source or of all. */
    List<SnapshotView> recentSnapshots(Optional<AuctionSource> source, int limit);

    /** When the newest run that reached at least one source finished. */
    Optional<Instant> lastCompletedRun();

    record RunView(
            long id,
            String trigger,
            String status,
            Instant startedAt,
            Instant finishedAt,
            int sourcesTotal,
            int snapshotsNew,
            int sourcesUnchanged,
            int sourcesFailed,
            String errorSummary) {}

    /**
     * @param checksNotPassed one {@code "DQ-Ixx OUTCOME: detail"} line per check that warned or failed
     */
    record SnapshotView(
            long id,
            String source,
            Instant lastModified,
            Instant fetchedAt,
            String status,
            Integer listingsRead,
            Integer listingsStaged,
            Instant processedAt,
            List<String> checksNotPassed) {}
}
