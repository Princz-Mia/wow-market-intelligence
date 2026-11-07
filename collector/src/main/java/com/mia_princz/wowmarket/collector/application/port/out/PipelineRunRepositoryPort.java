package com.mia_princz.wowmarket.collector.application.port.out;

import com.mia_princz.wowmarket.collector.domain.pipeline.CaptureSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.RunTrigger;
import java.time.Instant;

/** The run log in {@code ops.pipeline_run}, read by the data health dashboard. */
public interface PipelineRunRepositoryPort {

    /** Opens a run as RUNNING and returns its id. */
    long start(RunTrigger trigger, Instant startedAt, int sourcesTotal);

    /** Closes a run with its outcome. */
    void finish(long runId, Instant finishedAt, CaptureSummary summary);
}
