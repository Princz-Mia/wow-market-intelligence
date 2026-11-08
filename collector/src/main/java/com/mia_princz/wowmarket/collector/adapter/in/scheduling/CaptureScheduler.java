package com.mia_princz.wowmarket.collector.adapter.in.scheduling;

import com.mia_princz.wowmarket.collector.application.port.in.RunPipelineUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.RunPipelineUseCase.PipelineAlreadyRunningException;
import com.mia_princz.wowmarket.collector.domain.pipeline.RunTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Starts a pipeline run on the configured cron, and once right after startup, so a restart or redeploy does not wait
 * up to half an hour for the next snapshot.
 */
public class CaptureScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CaptureScheduler.class);

    private final RunPipelineUseCase pipeline;

    public CaptureScheduler(RunPipelineUseCase pipeline) {
        this.pipeline = pipeline;
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
            pipeline.run(RunTrigger.SCHEDULE);
        } catch (PipelineAlreadyRunningException e) {
            LOG.warn("run skipped: the previous one is still going");
        } catch (RuntimeException e) {
            // Each step reports its own failure; the schedule must survive anything else too.
            LOG.error("run failed", e);
        }
    }
}
