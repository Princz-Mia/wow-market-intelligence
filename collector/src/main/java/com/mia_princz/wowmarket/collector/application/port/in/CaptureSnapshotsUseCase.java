package com.mia_princz.wowmarket.collector.application.port.in;

import com.mia_princz.wowmarket.collector.domain.pipeline.CaptureSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.RunTrigger;

/** Fetches the current snapshot of every configured source and keeps the new ones. */
public interface CaptureSnapshotsUseCase {

    /** @throws CaptureAlreadyRunningException if another capture has not finished yet */
    CaptureSummary capture(RunTrigger trigger);

    final class CaptureAlreadyRunningException extends RuntimeException {
        public CaptureAlreadyRunningException() {
            super("a capture is already running");
        }
    }
}
