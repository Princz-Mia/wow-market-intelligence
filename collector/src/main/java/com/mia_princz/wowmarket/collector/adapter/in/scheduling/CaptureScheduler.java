package com.mia_princz.wowmarket.collector.adapter.in.scheduling;

import com.mia_princz.wowmarket.collector.application.port.in.CaptureSnapshotsUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.CaptureSnapshotsUseCase.CaptureAlreadyRunningException;
import com.mia_princz.wowmarket.collector.domain.pipeline.RunTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Starts a capture on the configured cron, and once right after startup, so a restart or redeploy does not wait up to
 * half an hour for the next snapshot.
 */
public class CaptureScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CaptureScheduler.class);

    private final CaptureSnapshotsUseCase capture;

    public CaptureScheduler(CaptureSnapshotsUseCase capture) {
        this.capture = capture;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void captureOnStartup() {
        run();
    }

    @Scheduled(cron = "${collector.capture.cron}", zone = "UTC")
    public void captureOnSchedule() {
        run();
    }

    private void run() {
        try {
            capture.capture(RunTrigger.SCHEDULE);
        } catch (CaptureAlreadyRunningException e) {
            LOG.warn("capture skipped: the previous one is still running");
        } catch (RuntimeException e) {
            // A failed run is recorded by the service; the schedule must survive it.
            LOG.error("capture failed", e);
        }
    }
}
