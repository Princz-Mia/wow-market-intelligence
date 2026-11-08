package com.mia_princz.wowmarket.collector.domain.quality;

import com.mia_princz.wowmarket.collector.domain.market.AuctionListing;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the ingestion checks over one snapshot. Listings are offered one at a time as they are parsed; each is either
 * kept for staging or dropped. Once the stream ends, {@link #finish()} decides on the snapshot as a whole.
 *
 * <p>Not thread-safe: one validator per snapshot.
 */
public final class SnapshotValidator {

    public static final String READABLE_ID = "DQ-I01";

    private final ValidationContext context;
    private final List<ListingRule> listingRules;
    private final List<BatchRule> batchRules;
    private final Map<String, Long> violations = new LinkedHashMap<>();
    private final Set<Integer> itemIds = new HashSet<>();
    private long[] auctionIds = new long[1 << 16];
    private int auctionIdCount;
    private long listingsRead;
    private long listingsKept;

    SnapshotValidator(ValidationContext context, List<ListingRule> listingRules, List<BatchRule> batchRules) {
        this.context = context;
        this.listingRules = List.copyOf(listingRules);
        this.batchRules = List.copyOf(batchRules);
        listingRules.forEach(rule -> violations.put(rule.id(), 0L));
    }

    /** The catalogue's checks, in catalogue order. */
    public static SnapshotValidator forSnapshot(ValidationContext context) {
        var required = new RequiredFieldsRule();
        var pricing = new PricingRule();
        var timeLeft = new KnownTimeLeftRule();
        return new SnapshotValidator(
                context,
                List.of(required, pricing, timeLeft),
                List.of(
                        new EventTimeAdvancesRule(),
                        new ViolationShareRule(required),
                        new ViolationShareRule(pricing),
                        new UniqueAuctionIdsRule(),
                        HistoryDeviationRule.listingCount(),
                        HistoryDeviationRule.distinctItems(),
                        new ViolationShareRule(timeLeft)));
    }

    /** The verdict for a payload that could not be read at all. */
    public static List<ValidationResult> unreadable(String reason) {
        return List.of(new ValidationResult.Failed(READABLE_ID, reason));
    }

    /**
     * Checks one listing.
     *
     * @return whether it should be staged
     */
    public boolean accept(AuctionListing listing) {
        listingsRead++;
        if (listing.id() != null) {
            rememberAuctionId(listing.id());
        }
        boolean keep = true;
        for (ListingRule rule : listingRules) {
            // A listing already dropped is not judged again by later dropping rules, so each is counted once.
            if ((keep || !rule.dropsListing()) && rule.isViolatedBy(listing, context.source())) {
                violations.merge(rule.id(), 1L, Long::sum);
                keep &= !rule.dropsListing();
            }
        }
        if (keep) {
            listingsKept++;
            itemIds.add(listing.item().id());
        }
        return keep;
    }

    public long listingsRead() {
        return listingsRead;
    }

    public long listingsKept() {
        return listingsKept;
    }

    /** Distinct item ids among the kept listings. */
    public int distinctItems() {
        return itemIds.size();
    }

    /** The verdict of every check, starting with the payload having been readable. */
    public List<ValidationResult> finish() {
        BatchFacts facts = new BatchFacts(
                listingsRead, listingsKept, new HashMap<>(violations), itemIds.size(), duplicateAuctionIds());
        List<ValidationResult> results = new ArrayList<>();
        results.add(new ValidationResult.Passed(READABLE_ID, listingsRead + " listings read"));
        batchRules.forEach(rule -> results.add(rule.evaluate(facts, context)));
        return results;
    }

    /** Whether any check failed, which keeps the snapshot out of staging. */
    public static boolean isBlocking(List<ValidationResult> results) {
        return results.stream().anyMatch(ValidationResult.Failed.class::isInstance);
    }

    private void rememberAuctionId(long id) {
        if (auctionIdCount == auctionIds.length) {
            auctionIds = Arrays.copyOf(auctionIds, auctionIds.length * 2);
        }
        auctionIds[auctionIdCount++] = id;
    }

    private long duplicateAuctionIds() {
        long[] sorted = Arrays.copyOf(auctionIds, auctionIdCount);
        Arrays.sort(sorted);
        long duplicates = 0;
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i] == sorted[i - 1]) {
                duplicates++;
            }
        }
        return duplicates;
    }
}
