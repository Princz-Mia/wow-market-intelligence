package com.mia_princz.wowmarket.collector.application.port.out;

import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.quality.ValidationContext.History;
import com.mia_princz.wowmarket.collector.domain.quality.ValidationResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** The registry's view for loading: what waits in the archive, what was loaded, and what each load decided. */
public interface SnapshotLoadRegistryPort {

    /** Archived snapshots not yet loaded or quarantined, oldest event time first. */
    List<PendingSnapshot> pending(String region, int limit);

    /** Event time of the newest loaded snapshot of a source. */
    Optional<Instant> lastLoaded(String region, AuctionSource source);

    /** Medians over the source's snapshots read in the seven days before {@code eventTime}. */
    History history(String region, AuctionSource source, Instant eventTime);

    /** Records the verdict of a load, replacing any earlier one for the snapshot. */
    void recordLoad(LoadRecord load);

    record PendingSnapshot(long id, AuctionSource source, Instant lastModified, String archiveLocation) {}

    /**
     * @param loaded true when the snapshot is staged, false when it is quarantined
     * @param listingsRead null when the payload could not be read
     * @param listingsStaged zero unless loaded
     * @param distinctItems null when the payload could not be read
     */
    record LoadRecord(
            long snapshotId,
            boolean loaded,
            Long listingsRead,
            long listingsStaged,
            Integer distinctItems,
            Instant processedAt,
            List<ValidationResult> results) {

        public LoadRecord {
            results = List.copyOf(results);
        }
    }
}
