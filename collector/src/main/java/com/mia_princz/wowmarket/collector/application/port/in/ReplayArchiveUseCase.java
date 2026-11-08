package com.mia_princz.wowmarket.collector.application.port.in;

import com.mia_princz.wowmarket.collector.domain.pipeline.ReplaySummary;
import java.time.Instant;

/**
 * Rebuilds staging from the raw archive: every snapshot from a point in time onward is checked and loaded again, as
 * after a fix to a check or to the parser.
 */
public interface ReplayArchiveUseCase {

    /**
     * @throws IllegalArgumentException if {@code from} lies before the days staging keeps, whose rows retention would
     *     drop again at the end of the next run
     * @throws RunPipelineUseCase.PipelineAlreadyRunningException if a run or another replay is going
     */
    ReplaySummary replayFrom(Instant from);
}
