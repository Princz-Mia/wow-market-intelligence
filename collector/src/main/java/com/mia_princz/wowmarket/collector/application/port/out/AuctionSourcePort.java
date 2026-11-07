package com.mia_princz.wowmarket.collector.application.port.out;

import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import java.time.Instant;
import java.util.Optional;

/** Fetches the current snapshot of an auction house, if it changed since the last one we have. */
public interface AuctionSourcePort {

    /**
     * @param modifiedSince event time of the newest snapshot already captured for this source, if any
     * @return {@link FetchResult.NotModified}, or the new snapshot, whose body the caller must close
     */
    FetchResult fetch(AuctionSource source, Optional<Instant> modifiedSince);
}
