package com.mia_princz.wowmarket.collector.application.port.out;

import com.mia_princz.wowmarket.collector.application.port.in.PipelineStatusUseCase.RunView;
import com.mia_princz.wowmarket.collector.application.port.in.PipelineStatusUseCase.SnapshotView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Read-only queries over the run log and the snapshot registry. */
public interface PipelineStatusPort {

    Optional<RunView> latestRun();

    /**
     * @param sourceKey a source's registry key, or empty for every source
     */
    List<SnapshotView> recentSnapshots(String region, Optional<String> sourceKey, int limit);

    Optional<Instant> lastCompletedRun();
}
