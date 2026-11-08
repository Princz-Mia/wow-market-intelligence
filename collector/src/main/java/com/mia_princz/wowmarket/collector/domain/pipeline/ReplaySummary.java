package com.mia_princz.wowmarket.collector.domain.pipeline;

/**
 * What a replay did.
 *
 * @param requeued snapshots put back in the load queue
 * @param load what loading them again decided, summed over every batch
 */
public record ReplaySummary(int requeued, LoadSummary load) {}
