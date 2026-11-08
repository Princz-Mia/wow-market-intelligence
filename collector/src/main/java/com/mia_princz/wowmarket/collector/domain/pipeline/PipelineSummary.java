package com.mia_princz.wowmarket.collector.domain.pipeline;

import java.util.List;

/**
 * What one pipeline run did, step by step. A step that failed outright has no summary and one line in
 * {@code stepErrors}; the steps after it still ran.
 */
public record PipelineSummary(
        CaptureSummary capture, LoadSummary load, RetentionSummary retention, List<String> stepErrors) {

    public PipelineSummary {
        stepErrors = List.copyOf(stepErrors);
    }
}
