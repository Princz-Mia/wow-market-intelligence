package com.mia_princz.wowmarket.collector.domain.quality;

/**
 * The outcome of one ingestion check on one snapshot. A failed check blocks the snapshot from staging; a warning is
 * recorded and the snapshot is loaded anyway.
 */
public sealed interface ValidationResult {

    /** The check's id from the data-quality catalogue, e.g. {@code DQ-I06}. */
    String ruleId();

    /** What was observed, in a few words, for the run record. */
    String detail();

    record Passed(String ruleId, String detail) implements ValidationResult {}

    record Warned(String ruleId, String detail) implements ValidationResult {}

    record Failed(String ruleId, String detail) implements ValidationResult {}
}
