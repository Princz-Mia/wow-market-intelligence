package com.mia_princz.wowmarket.collector.domain.quality;

import com.mia_princz.wowmarket.collector.domain.market.AuctionListing;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;

/** An ingestion check applied to every listing of a snapshot as it streams past. */
public interface ListingRule {

    /** The check's id from the data-quality catalogue. */
    String id();

    /** Whether a violating listing is left out of staging, or only counted. */
    boolean dropsListing();

    boolean isViolatedBy(AuctionListing listing, AuctionSource source);
}
