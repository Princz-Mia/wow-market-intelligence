package com.mia_princz.wowmarket.collector.application.port.in;

import com.mia_princz.wowmarket.collector.domain.pipeline.LoadSummary;

/** Moves archived snapshots through the ingestion checks into staging. */
public interface LoadSnapshotsUseCase {

    /**
     * Loads the archived snapshots waiting to be loaded, oldest first, up to the configured batch size.
     *
     * @throws LoadAlreadyRunningException if another load is still going
     */
    LoadSummary loadPending();

    final class LoadAlreadyRunningException extends RuntimeException {
        public LoadAlreadyRunningException() {
            super("a load is already running");
        }
    }
}
