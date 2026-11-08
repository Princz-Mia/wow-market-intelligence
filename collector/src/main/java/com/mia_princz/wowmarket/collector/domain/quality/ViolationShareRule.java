package com.mia_princz.wowmarket.collector.domain.quality;

import java.util.Locale;

/**
 * Turns a listing check's count into a verdict on the snapshot. For a dropping check, a few bad listings are dropped
 * with a warning, but more than 0.1% of the snapshot points at a broken payload and fails it. A counting-only check
 * warns when it saw anything.
 */
public final class ViolationShareRule implements BatchRule {

    static final double MAX_DROPPED_SHARE = 0.001;

    private final ListingRule rule;

    public ViolationShareRule(ListingRule rule) {
        this.rule = rule;
    }

    @Override
    public ValidationResult evaluate(BatchFacts facts, ValidationContext context) {
        long violations = facts.violationsOf(rule.id());
        if (violations == 0) {
            return new ValidationResult.Passed(rule.id(), "no listings flagged");
        }
        double share = (double) violations / facts.listingsRead();
        String detail = String.format(
                Locale.ROOT,
                "%d of %d listings flagged (%.3f%%)%s",
                violations,
                facts.listingsRead(),
                share * 100,
                rule.dropsListing() ? ", dropped" : "");
        return rule.dropsListing() && share > MAX_DROPPED_SHARE
                ? new ValidationResult.Failed(rule.id(), detail)
                : new ValidationResult.Warned(rule.id(), detail);
    }
}
