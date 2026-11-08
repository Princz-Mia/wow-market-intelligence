package com.mia_princz.wowmarket.collector.application.service;

import com.mia_princz.wowmarket.collector.application.port.in.PipelineStatusUseCase;
import com.mia_princz.wowmarket.collector.application.port.out.PipelineStatusPort;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class PipelineStatusService implements PipelineStatusUseCase {

    static final int MAX_SNAPSHOTS = 500;

    private final String region;
    private final PipelineStatusPort status;

    public PipelineStatusService(String region, PipelineStatusPort status) {
        this.region = region;
        this.status = status;
    }

    @Override
    public Optional<RunView> latestRun() {
        return status.latestRun();
    }

    @Override
    public List<SnapshotView> recentSnapshots(Optional<AuctionSource> source, int limit) {
        int bounded = Math.clamp(limit, 1, MAX_SNAPSHOTS);
        return status.recentSnapshots(region, source.map(AuctionSource::key), bounded);
    }

    @Override
    public Optional<Instant> lastCompletedRun() {
        return status.lastCompletedRun();
    }
}
