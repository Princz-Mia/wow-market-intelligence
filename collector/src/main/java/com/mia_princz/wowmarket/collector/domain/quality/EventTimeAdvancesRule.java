package com.mia_princz.wowmarket.collector.domain.quality;

/**
 * DQ-I03: a snapshot must be newer than the last one loaded for its source. Loading an older one would make time run
 * backwards in the warehouse.
 */
public final class EventTimeAdvancesRule implements BatchRule {

    public static final String ID = "DQ-I03";

    @Override
    public ValidationResult evaluate(BatchFacts facts, ValidationContext context) {
        return context.lastLoaded()
                .filter(last -> !context.lastModified().isAfter(last))
                .<ValidationResult>map(last -> new ValidationResult.Failed(
                        ID, "event time " + context.lastModified() + " not after last loaded " + last))
                .orElseGet(() -> new ValidationResult.Passed(ID, "event time " + context.lastModified()));
    }
}
