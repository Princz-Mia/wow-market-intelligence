package com.mia_princz.wowmarket.collector.application.port.out;

import java.time.Instant;

/** Puts processed snapshots back in the load queue, so they are loaded again from the archive. */
public interface SnapshotReplayPort {

    /**
     * Returns every loaded or quarantined snapshot of the region with an event time at or after {@code from} to the
     * archived state, dropping its earlier verdict.
     *
     * @return how many snapshots were queued again
     */
    int requeueFrom(String region, Instant from);
}
