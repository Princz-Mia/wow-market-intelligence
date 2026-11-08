package com.mia_princz.wowmarket.collector.domain.quality;

import static org.assertj.core.api.Assertions.assertThat;

import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.quality.ValidationContext.History;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BatchRulesTest {

    private static final Instant EVENT_TIME = Instant.parse("2026-11-07T20:21:53Z");
    private static final AuctionSource REALM = new AuctionSource.ConnectedRealm(1403);

    private static ValidationContext context(Optional<Instant> lastLoaded, History history) {
        return new ValidationContext(REALM, EVENT_TIME, lastLoaded, history);
    }

    private static BatchFacts facts(long read, long distinctItems, Map<String, Long> violations, long duplicates) {
        return new BatchFacts(read, read, violations, distinctItems, duplicates);
    }

    @Nested
    class EventTimeAdvances {

        private final BatchRule rule = new EventTimeAdvancesRule();
        private final BatchFacts any = facts(10, 5, Map.of(), 0);

        @Test
        void passesTheFirstSnapshotAndANewerOne() {
            assertThat(rule.evaluate(any, context(Optional.empty(), History.NONE)))
                    .isInstanceOf(ValidationResult.Passed.class);
            assertThat(rule.evaluate(any, context(Optional.of(EVENT_TIME.minusSeconds(3600)), History.NONE)))
                    .isInstanceOf(ValidationResult.Passed.class);
        }

        @Test
        void failsASnapshotThatIsNotNewerThanTheLastLoaded() {
            assertThat(rule.evaluate(any, context(Optional.of(EVENT_TIME), History.NONE)))
                    .isInstanceOf(ValidationResult.Failed.class);
            assertThat(rule.evaluate(any, context(Optional.of(EVENT_TIME.plusSeconds(1)), History.NONE)))
                    .isInstanceOf(ValidationResult.Failed.class);
        }
    }

    @Nested
    class UniqueAuctionIds {

        private final BatchRule rule = new UniqueAuctionIdsRule();

        @Test
        void failsOnAnyDuplicate() {
            ValidationContext context = context(Optional.empty(), History.NONE);

            assertThat(rule.evaluate(facts(10, 5, Map.of(), 0), context)).isInstanceOf(ValidationResult.Passed.class);
            assertThat(rule.evaluate(facts(10, 5, Map.of(), 1), context))
                    .isEqualTo(new ValidationResult.Failed("DQ-I06", "1 duplicate auction ids"));
        }
    }

    @Nested
    class ViolationShare {

        private final ValidationContext context = context(Optional.empty(), History.NONE);

        @Test
        void warnsWhileDroppedListingsStayWithinOnePerMille() {
            BatchRule rule = new ViolationShareRule(new RequiredFieldsRule());

            assertThat(rule.evaluate(facts(10_000, 5, Map.of(), 0), context))
                    .isInstanceOf(ValidationResult.Passed.class);
            assertThat(rule.evaluate(facts(10_000, 5, Map.of("DQ-I04", 10L), 0), context))
                    .isEqualTo(new ValidationResult.Warned("DQ-I04", "10 of 10000 listings flagged (0.100%), dropped"));
            assertThat(rule.evaluate(facts(10_000, 5, Map.of("DQ-I04", 11L), 0), context))
                    .isInstanceOf(ValidationResult.Failed.class);
        }

        @Test
        void onlyWarnsForACountingCheckHoweverManyItFlags() {
            BatchRule rule = new ViolationShareRule(new KnownTimeLeftRule());

            assertThat(rule.evaluate(facts(100, 5, Map.of("DQ-I09", 60L), 0), context))
                    .isEqualTo(new ValidationResult.Warned("DQ-I09", "60 of 100 listings flagged (60.000%)"));
        }
    }

    @Nested
    class HistoryDeviation {

        private final History day = new History(48, 80_000, 16_000);

        @Test
        void passesWhileThereIsLessThanADayOfHistory() {
            BatchRule rule = HistoryDeviationRule.listingCount();

            assertThat(rule.evaluate(facts(10, 5, Map.of(), 0), context(Optional.empty(), new History(23, 80_000, 0))))
                    .isEqualTo(new ValidationResult.Passed("DQ-I07", "10 listings; not enough history (23 snapshots)"));
        }

        @Test
        void warnsBeyondHalfAndFailsBeyondFourFifthsOfTheMedianListingCount() {
            BatchRule rule = HistoryDeviationRule.listingCount();
            ValidationContext context = context(Optional.empty(), day);

            assertThat(rule.evaluate(facts(120_000, 5, Map.of(), 0), context))
                    .isEqualTo(
                            new ValidationResult.Passed("DQ-I07", "120000 listings vs trailing median 80000 (+50%)"));
            assertThat(rule.evaluate(facts(30_000, 5, Map.of(), 0), context))
                    .isInstanceOf(ValidationResult.Warned.class);
            assertThat(rule.evaluate(facts(15_000, 5, Map.of(), 0), context))
                    .isInstanceOf(ValidationResult.Failed.class);
        }

        @Test
        void onlyWarnsAboutTheDistinctItemCount() {
            BatchRule rule = HistoryDeviationRule.distinctItems();
            ValidationContext context = context(Optional.empty(), day);

            assertThat(rule.evaluate(facts(80_000, 16_500, Map.of(), 0), context))
                    .isInstanceOf(ValidationResult.Passed.class);
            assertThat(rule.evaluate(facts(80_000, 1_000, Map.of(), 0), context))
                    .isInstanceOf(ValidationResult.Warned.class);
        }
    }
}
