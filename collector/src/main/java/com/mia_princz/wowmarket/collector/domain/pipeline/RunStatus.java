package com.mia_princz.wowmarket.collector.domain.pipeline;

/** Final state of a pipeline run; {@link #RUNNING} until it closes. */
public enum RunStatus {
    RUNNING,
    SUCCEEDED,
    PARTIAL,
    FAILED
}
