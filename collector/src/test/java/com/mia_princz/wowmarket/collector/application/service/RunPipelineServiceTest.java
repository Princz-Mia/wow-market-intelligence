package com.mia_princz.wowmarket.collector.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mia_princz.wowmarket.collector.application.port.in.ApplyRetentionUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.CaptureSnapshotsUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.LoadSnapshotsUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.RunPipelineUseCase.PipelineAlreadyRunningException;
import com.mia_princz.wowmarket.collector.domain.pipeline.CaptureSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.LoadSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.PipelineSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.RetentionSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.RunTrigger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RunPipelineServiceTest {

    private static final CaptureSummary CAPTURED = new CaptureSummary(9, 9, 0, List.of());
    private static final LoadSummary LOADED = new LoadSummary(9, 0, 0, 725_000);
    private static final RetentionSummary NOTHING_REMOVED = new RetentionSummary(List.of(), 0);

    private final List<String> steps = new ArrayList<>();
    private final LoadSnapshotsUseCase load = () -> {
        steps.add("load");
        return LOADED;
    };
    private final ApplyRetentionUseCase retention = () -> {
        steps.add("retention");
        return NOTHING_REMOVED;
    };

    @Test
    void capturesThenLoadsThenAppliesRetention() {
        CaptureSnapshotsUseCase capture = trigger -> {
            steps.add("capture " + trigger);
            return CAPTURED;
        };

        PipelineSummary summary = new RunPipelineService(capture, load, retention).run(RunTrigger.MANUAL);

        assertThat(steps).containsExactly("capture MANUAL", "load", "retention");
        assertThat(summary).isEqualTo(new PipelineSummary(CAPTURED, LOADED, NOTHING_REMOVED, List.of()));
    }

    @Test
    void stillLoadsAndCleansUpWhenTheCaptureFails() {
        CaptureSnapshotsUseCase capture = trigger -> {
            throw new IllegalStateException("database unreachable");
        };

        PipelineSummary summary = new RunPipelineService(capture, load, retention).run(RunTrigger.SCHEDULE);

        assertThat(steps).containsExactly("load", "retention");
        assertThat(summary.capture()).isNull();
        assertThat(summary.stepErrors()).containsExactly("capture: database unreachable");
    }

    @Test
    void refusesASecondRunWhileOneIsGoing() throws Exception {
        CountDownLatch inCapture = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CaptureSnapshotsUseCase capture = trigger -> {
            inCapture.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return CAPTURED;
        };
        var service = new RunPipelineService(capture, load, retention);
        Thread first = Thread.ofVirtual().start(() -> service.run(RunTrigger.SCHEDULE));
        assertThat(inCapture.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> service.run(RunTrigger.MANUAL)).isInstanceOf(PipelineAlreadyRunningException.class);

        release.countDown();
        first.join();
    }
}
