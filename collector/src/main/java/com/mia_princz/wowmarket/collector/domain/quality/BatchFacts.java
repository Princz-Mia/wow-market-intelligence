package com.mia_princz.wowmarket.collector.domain.quality;

import java.util.Map;

/**
 * What streaming one snapshot past the listing checks revealed.
 *
 * @param listingsRead every listing in the payload
 * @param listingsKept listings no dropping check rejected
 * @param violations listings each listing check flagged, by check id
 * @param distinctItems distinct item ids among the kept listings
 * @param duplicateAuctionIds auction ids that appeared more than once, counting each extra occurrence
 */
public record BatchFacts(
        long listingsRead,
        long listingsKept,
        Map<String, Long> violations,
        long distinctItems,
        long duplicateAuctionIds) {

    public BatchFacts {
        violations = Map.copyOf(violations);
    }

    public long violationsOf(String ruleId) {
        return violations.getOrDefault(ruleId, 0L);
    }
}
