package com.mia_princz.wowmarket.collector.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mia_princz.wowmarket.collector.application.port.in.LoadSnapshotsUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.RunPipelineUseCase.PipelineAlreadyRunningException;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotReplayPort;
import com.mia_princz.wowmarket.collector.domain.pipeline.LoadSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.ReplaySummary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplayArchiveServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-08T10:30:00Z"), ZoneOffset.UTC);

    private final List<String> calls = new ArrayList<>();
    private final Deque<LoadSummary> batches = new ArrayDeque<>();
    private final SnapshotReplayPort replay = (region, from) -> {
        calls.add("requeue " + region + " " + from);
        return 150;
    };
    private final LoadSnapshotsUseCase load = () -> {
        calls.add("load");
        return batches.isEmpty() ? new LoadSummary(0, 0, 0, 0) : batches.pop();
    };
    private final PipelineLock lock = new PipelineLock();
    private final ReplayArchiveService service = new ReplayArchiveService("eu", 3, replay, load, lock, CLOCK);

    @Test
    void requeuesThenLoadsBatchesUntilTheQueueIsEmpty() {
        batches.add(new LoadSummary(100, 0, 0, 7_000_000));
        batches.add(new LoadSummary(49, 1, 0, 3_400_000));

        ReplaySummary summary = service.replayFrom(Instant.parse("2026-11-05T00:00:00Z"));

        assertThat(calls).containsExactly("requeue eu 2026-11-05T00:00:00Z", "load", "load", "load");
        assertThat(summary).isEqualTo(new ReplaySummary(150, new LoadSummary(149, 1, 0, 10_400_000)));
    }

    @Test
    void stopsWhenOnlyFailingSnapshotsAreLeft() {
        batches.add(new LoadSummary(0, 0, 2, 0));
        batches.add(new LoadSummary(0, 0, 2, 0));

        ReplaySummary summary = service.replayFrom(Instant.parse("2026-11-08T00:00:00Z"));

        assertThat(calls).containsExactly("requeue eu 2026-11-08T00:00:00Z", "load");
        assertThat(summary.load().failed()).isEqualTo(2);
    }

    @Test
    void refusesToReplayDaysThatRetentionWouldDropAgain() {
        assertThatThrownBy(() -> service.replayFrom(Instant.parse("2026-11-04T23:59:59Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2026-11-05");
        assertThat(calls).isEmpty();
    }

    @Test
    void refusesToReplayWhileARunHoldsThePipeline() {
        lock.exclusively(() -> {
            assertThatThrownBy(() -> service.replayFrom(Instant.parse("2026-11-08T00:00:00Z")))
                    .isInstanceOf(PipelineAlreadyRunningException.class);
            return null;
        });
        assertThat(calls).isEmpty();
    }
}
