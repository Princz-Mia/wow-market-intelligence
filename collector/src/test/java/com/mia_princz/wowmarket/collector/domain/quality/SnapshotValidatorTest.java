package com.mia_princz.wowmarket.collector.domain.quality;

import static org.assertj.core.api.Assertions.assertThat;

import com.mia_princz.wowmarket.collector.domain.market.AuctionListing;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.market.ListedItem;
import com.mia_princz.wowmarket.collector.domain.quality.ValidationContext.History;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class SnapshotValidatorTest {

    private static final ValidationContext COMMODITIES = new ValidationContext(
            new AuctionSource.RegionCommodities(),
            Instant.parse("2026-11-07T20:21:53Z"),
            Optional.empty(),
            History.NONE);

    private static AuctionListing listing(long id, int item, Integer quantity, Long unitPrice, String timeLeft) {
        return new AuctionListing(id, ListedItem.commodity(item), quantity, unitPrice, null, null, timeLeft);
    }

    private static List<ValidationResult> validate(SnapshotValidator validator, List<AuctionListing> listings) {
        listings.forEach(validator::accept);
        return validator.finish();
    }

    @Test
    void keepsGoodListingsAndDropsBadOnes() {
        SnapshotValidator validator = SnapshotValidator.forSnapshot(COMMODITIES);

        assertThat(validator.accept(listing(1, 190396, 200, 1_250_000L, "SHORT")))
                .isTrue();
        assertThat(validator.accept(listing(2, 190396, null, 1_250_000L, "SHORT")))
                .isFalse();
        assertThat(validator.accept(listing(3, 190396, 5, 0L, "SHORT"))).isFalse();
        assertThat(validator.accept(listing(4, 190396, 5, 100L, "FOREVER"))).isTrue();
    }

    @Test
    void reportsEveryCheckInCatalogueOrder() {
        List<ValidationResult> results = validate(
                SnapshotValidator.forSnapshot(COMMODITIES),
                List.of(listing(1, 190396, 200, 1_250_000L, "SHORT"), listing(2, 191383, 1, 98_000_000L, "LONG")));

        assertThat(results)
                .extracting(ValidationResult::ruleId)
                .containsExactly("DQ-I01", "DQ-I03", "DQ-I04", "DQ-I05", "DQ-I06", "DQ-I07", "DQ-I08", "DQ-I09");
        assertThat(results).allMatch(ValidationResult.Passed.class::isInstance);
        assertThat(results.getFirst().detail()).isEqualTo("2 listings read");
        assertThat(SnapshotValidator.isBlocking(results)).isFalse();
    }

    @Test
    void countsADroppedListingUnderTheFirstCheckThatRejectedIt() {
        // Missing quantity breaks both DQ-I04 and DQ-I05; it must count once, under DQ-I04.
        List<AuctionListing> listings = LongStream.rangeClosed(1, 2000)
                .mapToObj(id -> listing(id, 190396, id == 7 ? null : 1, 100L, "SHORT"))
                .toList();

        List<ValidationResult> results = validate(SnapshotValidator.forSnapshot(COMMODITIES), listings);

        assertThat(results)
                .filteredOn(result -> result.ruleId().equals("DQ-I04"))
                .containsExactly(new ValidationResult.Warned("DQ-I04", "1 of 2000 listings flagged (0.050%), dropped"));
        assertThat(results)
                .filteredOn(result -> result.ruleId().equals("DQ-I05"))
                .allMatch(ValidationResult.Passed.class::isInstance);
        assertThat(SnapshotValidator.isBlocking(results)).isFalse();
    }

    @Test
    void blocksASnapshotWithDuplicateAuctionIds() {
        List<ValidationResult> results = validate(
                SnapshotValidator.forSnapshot(COMMODITIES),
                List.of(listing(1, 190396, 1, 100L, "SHORT"), listing(1, 191383, 1, 100L, "SHORT")));

        assertThat(results).contains(new ValidationResult.Failed("DQ-I06", "1 duplicate auction ids"));
        assertThat(SnapshotValidator.isBlocking(results)).isTrue();
    }

    @Test
    void findsDuplicatesBeyondItsInitialCapacity() {
        SnapshotValidator validator = SnapshotValidator.forSnapshot(COMMODITIES);
        LongStream.rangeClosed(1, 200_000).forEach(id -> validator.accept(listing(id, 190396, 1, 100L, "SHORT")));
        validator.accept(listing(150_000, 190396, 1, 100L, "SHORT"));

        assertThat(validator.finish()).contains(new ValidationResult.Failed("DQ-I06", "1 duplicate auction ids"));
    }

    @Test
    void blocksAnUnreadablePayload() {
        List<ValidationResult> results = SnapshotValidator.unreadable("the body has no auctions array");

        assertThat(results).containsExactly(new ValidationResult.Failed("DQ-I01", "the body has no auctions array"));
        assertThat(SnapshotValidator.isBlocking(results)).isTrue();
    }
}
