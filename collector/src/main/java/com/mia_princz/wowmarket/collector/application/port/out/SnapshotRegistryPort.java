package com.mia_princz.wowmarket.collector.application.port.out;

import com.mia_princz.wowmarket.collector.application.port.out.RawArchivePort.ArchivedSnapshot;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import java.time.Instant;
import java.util.Optional;

/** Which snapshots we already have. A source and its event time identify a snapshot. */
public interface SnapshotRegistryPort {

    /** Event time of the newest snapshot captured for this source. */
    Optional<Instant> latestEventTime(String region, AuctionSource source);

    /** Records a newly archived snapshot; returns false if it was already registered. */
    boolean register(NewSnapshot snapshot);

    record NewSnapshot(
            String region,
            AuctionSource source,
            Instant lastModified,
            Instant fetchedAt,
            long pipelineRunId,
            ArchivedSnapshot archived) {}
}
