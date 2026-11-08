package com.mia_princz.wowmarket.collector.application.port.in;

import com.mia_princz.wowmarket.collector.domain.pipeline.PipelineSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.RunTrigger;

/** One pass of the pipeline: capture, load, then retention. */
public interface RunPipelineUseCase {

    /**
     * Runs every step in order. A failing step is reported and does not stop the steps after it.
     *
     * @throws PipelineAlreadyRunningException if a run is still going
     */
    PipelineSummary run(RunTrigger trigger);

    final class PipelineAlreadyRunningException extends RuntimeException {
        public PipelineAlreadyRunningException() {
            super("a pipeline run is already going");
        }
    }
}
