package com.mia_princz.wowmarket.collector.domain.quality;

import com.mia_princz.wowmarket.collector.domain.market.AuctionListing;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;

/** DQ-I04: a listing needs an auction id, an item id, a quantity and at least one price. */
public final class RequiredFieldsRule implements ListingRule {

    public static final String ID = "DQ-I04";

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
        boolean hasPrice = listing.unitPrice() != null || listing.buyout() != null || listing.bid() != null;
        return listing.id() == null
                || listing.item() == null
                || listing.item().id() == null
                || listing.quantity() == null
                || !hasPrice;
    }
}
