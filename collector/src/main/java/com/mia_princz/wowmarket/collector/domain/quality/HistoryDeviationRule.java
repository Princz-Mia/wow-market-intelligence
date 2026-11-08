package com.mia_princz.wowmarket.collector.domain.quality;

import java.util.Locale;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * Compares a snapshot with its own source's recent past instead of a fixed number, since a busy realm and a quiet one
 * differ by an order of magnitude. Until a day of loaded snapshots exists there is nothing to compare with, and the
 * check passes.
 */
public final class HistoryDeviationRule implements BatchRule {

    /** About one day of hourly snapshots. */
    static final int MIN_HISTORY = 24;

    private final String id;
    private final String metric;
    private final ToLongFunction<BatchFacts> observed;
    private final ToDoubleFunction<ValidationContext.History> median;
    private final double warnDeviation;
    private final double failDeviation;

    private HistoryDeviationRule(
            String id,
            String metric,
            ToLongFunction<BatchFacts> observed,
            ToDoubleFunction<ValidationContext.History> median,
            double warnDeviation,
            double failDeviation) {
        this.id = id;
        this.metric = metric;
        this.observed = observed;
        this.median = median;
        this.warnDeviation = warnDeviation;
        this.failDeviation = failDeviation;
    }

    /** DQ-I07: listing count within ±50% of the trailing median warns beyond it and fails beyond ±80%. */
    public static HistoryDeviationRule listingCount() {
        return new HistoryDeviationRule(
                "DQ-I07", "listings", BatchFacts::listingsRead, ValidationContext.History::medianListings, 0.5, 0.8);
    }

    /** DQ-I08: distinct item count within ±50% of the trailing median, otherwise a warning. */
    public static HistoryDeviationRule distinctItems() {
        return new HistoryDeviationRule(
                "DQ-I08",
                "distinct items",
                BatchFacts::distinctItems,
                ValidationContext.History::medianDistinctItems,
                0.5,
                Double.POSITIVE_INFINITY);
    }

    @Override
    public ValidationResult evaluate(BatchFacts facts, ValidationContext context) {
        long value = observed.applyAsLong(facts);
        ValidationContext.History history = context.history();
        double expected = median.applyAsDouble(history);
        if (history.snapshots() < MIN_HISTORY || expected <= 0) {
            return new ValidationResult.Passed(
                    id, value + " " + metric + "; not enough history (" + history.snapshots() + " snapshots)");
        }
        double deviation = Math.abs(value - expected) / expected;
        String detail = String.format(
                Locale.ROOT,
                "%d %s vs trailing median %.0f (%+.0f%%)",
                value,
                metric,
                expected,
                (value - expected) / expected * 100);
        if (deviation > failDeviation) {
            return new ValidationResult.Failed(id, detail);
        }
        return deviation > warnDeviation
                ? new ValidationResult.Warned(id, detail)
                : new ValidationResult.Passed(id, detail);
    }
}
