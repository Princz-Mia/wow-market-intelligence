package com.mia_princz.wowmarket.collector.domain.quality;

import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * What the checks need to know about a snapshot besides its listings.
 *
 * @param lastLoaded event time of the newest snapshot of this source already loaded, if any
 * @param history the source's recent loaded snapshots, for history-based expectations
 */
public record ValidationContext(
        AuctionSource source, Instant lastModified, Optional<Instant> lastLoaded, History history) {

    public ValidationContext {
        Objects.requireNonNull(source);
        Objects.requireNonNull(lastModified);
        Objects.requireNonNull(lastLoaded);
        Objects.requireNonNull(history);
    }

    /**
     * Medians over the source's loaded snapshots in the trailing window.
     *
     * @param snapshots how many loaded snapshots the medians are based on
     */
    public record History(int snapshots, double medianListings, double medianDistinctItems) {
        public static final History NONE = new History(0, 0, 0);
    }
}
