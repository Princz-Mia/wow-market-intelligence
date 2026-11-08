package com.mia_princz.wowmarket.collector.application.service;

import com.mia_princz.wowmarket.collector.application.port.in.ApplyRetentionUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.CaptureSnapshotsUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.LoadSnapshotsUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.RunPipelineUseCase;
import com.mia_princz.wowmarket.collector.domain.pipeline.CaptureSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.LoadSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.PipelineSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.RetentionSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.RunTrigger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Runs capture, load and retention in that order, one run at a time. Capturing is what cannot be repeated later, so it
 * goes first and a slow or failing later step never delays it; a failed capture still lets the load work off whatever
 * waits in the archive.
 */
public class RunPipelineService implements RunPipelineUseCase {

    private static final System.Logger LOG = System.getLogger(RunPipelineService.class.getName());

    private final CaptureSnapshotsUseCase capture;
    private final LoadSnapshotsUseCase load;
    private final ApplyRetentionUseCase retention;
    private final ReentrantLock running = new ReentrantLock();

    public RunPipelineService(
            CaptureSnapshotsUseCase capture, LoadSnapshotsUseCase load, ApplyRetentionUseCase retention) {
        this.capture = capture;
        this.load = load;
        this.retention = retention;
    }

    @Override
    public PipelineSummary run(RunTrigger trigger) {
        if (!running.tryLock()) {
            throw new PipelineAlreadyRunningException();
        }
        try {
            List<String> errors = new ArrayList<>();
            CaptureSummary captured = step("capture", () -> capture.capture(trigger), errors);
            LoadSummary loaded = step("load", load::loadPending, errors);
            if (loaded != null) {
                LOG.log(
                        loaded.failed() == 0 ? Level.INFO : Level.WARNING,
                        () -> "load loaded=" + loaded.loaded() + " quarantined=" + loaded.quarantined() + " failed="
                                + loaded.failed() + " staged=" + loaded.listingsStaged());
            }
            RetentionSummary removed = step("retention", retention::apply, errors);
            return new PipelineSummary(captured, loaded, removed, errors);
        } finally {
            running.unlock();
        }
    }

    private static <T> T step(String name, Supplier<T> work, List<String> errors) {
        try {
            return work.get();
        } catch (RuntimeException e) {
            LOG.log(Level.ERROR, () -> name + " failed", e);
            errors.add(name + ": " + e.getMessage());
            return null;
        }
    }
}
