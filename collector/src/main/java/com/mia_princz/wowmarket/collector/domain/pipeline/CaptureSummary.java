package com.mia_princz.wowmarket.collector.domain.pipeline;

import java.util.List;

/**
 * What one capture step achieved across its sources.
 *
 * @param errors one short line per failed source, for the run record
 */
public record CaptureSummary(int sourcesTotal, int snapshotsNew, int sourcesUnchanged, List<String> errors) {

    public CaptureSummary {
        errors = List.copyOf(errors);
        if (snapshotsNew + sourcesUnchanged + errors.size() != sourcesTotal) {
            throw new IllegalArgumentException("every source must be new, unchanged or failed exactly once");
        }
    }

    public int sourcesFailed() {
        return errors.size();
    }

    /** All sources answered: succeeded. Some failed: partial. None answered: failed. */
    public RunStatus status() {
        if (errors.isEmpty()) {
            return RunStatus.SUCCEEDED;
        }
        return errors.size() == sourcesTotal ? RunStatus.FAILED : RunStatus.PARTIAL;
    }
}
