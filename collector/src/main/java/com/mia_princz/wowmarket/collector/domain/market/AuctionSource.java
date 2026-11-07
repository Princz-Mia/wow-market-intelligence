package com.mia_princz.wowmarket.collector.domain.market;

/**
 * One auction house snapshot we capture. Commodities trade on a single region-wide auction house; everything else
 * trades per connected realm.
 */
public sealed interface AuctionSource permits AuctionSource.RegionCommodities, AuctionSource.ConnectedRealm {

    /** Stable name used in the archive path and the snapshot registry, e.g. {@code connected-realm-1403}. */
    String key();

    /** The source a {@link #key()} names. */
    static AuctionSource fromKey(String key) {
        if ("commodities".equals(key)) {
            return new RegionCommodities();
        }
        if (key != null && key.startsWith(ConnectedRealm.KEY_PREFIX)) {
            try {
                return new ConnectedRealm(Integer.parseInt(key.substring(ConnectedRealm.KEY_PREFIX.length())));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("not a source key: " + key, e);
            }
        }
        throw new IllegalArgumentException("not a source key: " + key);
    }

    record RegionCommodities() implements AuctionSource {
        @Override
        public String key() {
            return "commodities";
        }
    }

    record ConnectedRealm(int connectedRealmId) implements AuctionSource {
        private static final String KEY_PREFIX = "connected-realm-";

        public ConnectedRealm {
            if (connectedRealmId <= 0) {
                throw new IllegalArgumentException("connected realm id must be positive: " + connectedRealmId);
            }
        }

        @Override
        public String key() {
            return KEY_PREFIX + connectedRealmId;
        }
    }
}
