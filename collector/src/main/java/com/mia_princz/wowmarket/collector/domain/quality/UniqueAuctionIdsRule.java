package com.mia_princz.wowmarket.collector.domain.quality;

/** DQ-I06: no auction id appears twice in one snapshot. A duplicate means the payload cannot be trusted. */
public final class UniqueAuctionIdsRule implements BatchRule {

    public static final String ID = "DQ-I06";

    @Override
    public ValidationResult evaluate(BatchFacts facts, ValidationContext context) {
        long duplicates = facts.duplicateAuctionIds();
        return duplicates == 0
                ? new ValidationResult.Passed(ID, "no duplicate auction ids")
                : new ValidationResult.Failed(ID, duplicates + " duplicate auction ids");
    }
}
