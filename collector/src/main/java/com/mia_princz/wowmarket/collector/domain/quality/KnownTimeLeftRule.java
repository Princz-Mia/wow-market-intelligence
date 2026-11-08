package com.mia_princz.wowmarket.collector.domain.quality;

import com.mia_princz.wowmarket.collector.domain.market.AuctionListing;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.market.TimeLeft;

/**
 * DQ-I09: {@code time_left} is one of the known buckets. An unknown value hints at an API change, but the listing is
 * still usable, so it is only counted.
 */
public final class KnownTimeLeftRule implements ListingRule {

    public static final String ID = "DQ-I09";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean dropsListing() {
        return false;
    }

    @Override
    public boolean isViolatedBy(AuctionListing listing, AuctionSource source) {
        return TimeLeft.fromApi(listing.timeLeft()).isEmpty();
    }
}
