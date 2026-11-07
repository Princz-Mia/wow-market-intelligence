package com.mia_princz.wowmarket.collector.domain.quality;

import static org.assertj.core.api.Assertions.assertThat;

import com.mia_princz.wowmarket.collector.domain.market.AuctionListing;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.market.ListedItem;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ListingRulesTest {

    private static final AuctionSource COMMODITIES = new AuctionSource.RegionCommodities();
    private static final AuctionSource REALM = new AuctionSource.ConnectedRealm(1403);
    private static final ListedItem ITEM = ListedItem.commodity(190396);

    private static AuctionListing commodity(Integer quantity, Long unitPrice) {
        return new AuctionListing(1L, ITEM, quantity, unitPrice, null, null, "SHORT");
    }

    private static AuctionListing realm(Long buyout, Long bid) {
        return new AuctionListing(1L, ITEM, 1, null, buyout, bid, "LONG");
    }

    @Nested
    class RequiredFields {

        private final ListingRule rule = new RequiredFieldsRule();

        @Test
        void acceptsACompleteListing() {
            assertThat(rule.isViolatedBy(commodity(5, 100L), COMMODITIES)).isFalse();
            assertThat(rule.isViolatedBy(realm(null, 100L), REALM)).isFalse();
        }

        @Test
        void flagsEachMissingField() {
            assertThat(rule.isViolatedBy(new AuctionListing(null, ITEM, 5, 100L, null, null, "SHORT"), COMMODITIES))
                    .isTrue();
            assertThat(rule.isViolatedBy(new AuctionListing(1L, null, 5, 100L, null, null, "SHORT"), COMMODITIES))
                    .isTrue();
            assertThat(rule.isViolatedBy(
                            new AuctionListing(
                                    1L,
                                    new ListedItem(null, null, null, null, null, null, null),
                                    5,
                                    100L,
                                    null,
                                    null,
                                    "SHORT"),
                            COMMODITIES))
                    .isTrue();
            assertThat(rule.isViolatedBy(commodity(null, 100L), COMMODITIES)).isTrue();
            assertThat(rule.isViolatedBy(realm(null, null), REALM)).isTrue();
        }

        @Test
        void dropsViolatingListings() {
            assertThat(rule.dropsListing()).isTrue();
        }
    }

    @Nested
    class Pricing {

        private final ListingRule rule = new PricingRule();

        @Test
        void acceptsACommodityPricedPerUnit() {
            assertThat(rule.isViolatedBy(commodity(200, 1_250_000L), COMMODITIES))
                    .isFalse();
        }

        @Test
        void acceptsARealmListingWithBuyoutBidOrBoth() {
            assertThat(rule.isViolatedBy(realm(2_000_000L, null), REALM)).isFalse();
            assertThat(rule.isViolatedBy(realm(null, 120L), REALM)).isFalse();
            assertThat(rule.isViolatedBy(realm(2_000_000L, 1_500_000L), REALM)).isFalse();
        }

        @Test
        void flagsAPricingModeThatDoesNotMatchTheSource() {
            assertThat(rule.isViolatedBy(new AuctionListing(1L, ITEM, 1, 100L, 100L, null, "SHORT"), COMMODITIES))
                    .isTrue();
            assertThat(rule.isViolatedBy(new AuctionListing(1L, ITEM, 1, 100L, null, null, "SHORT"), REALM))
                    .isTrue();
            assertThat(rule.isViolatedBy(realm(null, null), REALM)).isTrue();
        }

        @Test
        void flagsZeroOrNegativeQuantitiesAndPrices() {
            assertThat(rule.isViolatedBy(commodity(0, 100L), COMMODITIES)).isTrue();
            assertThat(rule.isViolatedBy(commodity(5, 0L), COMMODITIES)).isTrue();
            assertThat(rule.isViolatedBy(realm(2_000_000L, -1L), REALM)).isTrue();
        }
    }

    @Nested
    class KnownTimeLeft {

        private final ListingRule rule = new KnownTimeLeftRule();

        @Test
        void flagsAnUnknownOrMissingBucket() {
            assertThat(rule.isViolatedBy(commodity(5, 100L), COMMODITIES)).isFalse();
            assertThat(rule.isViolatedBy(new AuctionListing(1L, ITEM, 5, 100L, null, null, "FOREVER"), COMMODITIES))
                    .isTrue();
            assertThat(rule.isViolatedBy(new AuctionListing(1L, ITEM, 5, 100L, null, null, null), COMMODITIES))
                    .isTrue();
        }

        @Test
        void keepsTheListingAnyway() {
            assertThat(rule.dropsListing()).isFalse();
        }
    }
}
