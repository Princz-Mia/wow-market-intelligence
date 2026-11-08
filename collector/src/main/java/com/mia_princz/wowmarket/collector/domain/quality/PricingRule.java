package com.mia_princz.wowmarket.collector.domain.quality;

import com.mia_princz.wowmarket.collector.domain.market.AuctionListing;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;

/**
 * DQ-I05: quantity and every price present are positive, and the listing uses its source's pricing mode. Commodities
 * are priced per unit and nothing else. Realm listings have a buyout, a bid, or both, and never a unit price; when both
 * are present the buyout is the price.
 */
public final class PricingRule implements ListingRule {

    public static final String ID = "DQ-I05";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean dropsListing() {
        return true;
    }

    @Override
    public boolean isViolatedBy(AuctionListing listing, AuctionSource source) {
        if (!positive(listing.quantity())
                || !positiveOrAbsent(listing.unitPrice())
                || !positiveOrAbsent(listing.buyout())
                || !positiveOrAbsent(listing.bid())) {
            return true;
        }
        return switch (source) {
            case AuctionSource.RegionCommodities ignored ->
                listing.unitPrice() == null || listing.buyout() != null || listing.bid() != null;
            case AuctionSource.ConnectedRealm ignored ->
                listing.unitPrice() != null || (listing.buyout() == null && listing.bid() == null);
        };
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private static boolean positiveOrAbsent(Long value) {
        return value == null || value > 0;
    }
}
