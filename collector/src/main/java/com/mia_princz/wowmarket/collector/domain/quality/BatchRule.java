package com.mia_princz.wowmarket.collector.domain.quality;

/** An ingestion check on a snapshot as a whole, decided once all its listings have streamed past. */
public interface BatchRule {

    ValidationResult evaluate(BatchFacts facts, ValidationContext context);
}
