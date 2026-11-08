package com.mia_princz.wowmarket.collector.domain.pipeline;

/**
 * What one load step did with the archived snapshots waiting for it.
 *
 * @param loaded snapshots that passed the ingestion checks and are staged
 * @param quarantined snapshots a blocking check kept out of staging; their files stay in the archive
 * @param failed snapshots that could not be processed this time and will be tried again
 */
public record LoadSummary(int loaded, int quarantined, int failed, long listingsStaged) {}
