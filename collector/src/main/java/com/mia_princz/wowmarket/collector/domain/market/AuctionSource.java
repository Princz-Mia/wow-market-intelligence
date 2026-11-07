package com.mia_princz.wowmarket.collector.domain.market;

/**
 * One auction house snapshot we capture. Commodities trade on a single region-wide auction house; everything else
 * trades per connected realm.
 */
public sealed interface AuctionSource permits AuctionSource.RegionCommodities, AuctionSource.ConnectedRealm {

    /** Stable name used in the archive path and the snapshot registry, e.g. {@code connected-realm-1403}. */
    String key();

    record RegionCommodities() implements AuctionSource {
        @Override
        public String key() {
            return "commodities";
        }
    }

    record ConnectedRealm(int connectedRealmId) implements AuctionSource {
        public ConnectedRealm {
            if (connectedRealmId <= 0) {
                throw new IllegalArgumentException("connected realm id must be positive: " + connectedRealmId);
            }
        }

        @Override
        public String key() {
            return "connected-realm-" + connectedRealmId;
        }
    }
}
