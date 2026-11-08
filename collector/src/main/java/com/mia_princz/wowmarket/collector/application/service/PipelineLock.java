package com.mia_princz.wowmarket.collector.application.service;

import com.mia_princz.wowmarket.collector.application.port.in.RunPipelineUseCase.PipelineAlreadyRunningException;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * One pipeline at a time: a scheduled run, a manual run and a replay all change the load queue and staging, so they
 * take turns instead of waiting on each other.
 */
public final class PipelineLock {

    /** Not a reentrant lock: a run must not start a replay, or the other way round, even on its own thread. */
    private final Semaphore permit = new Semaphore(1);

    /**
     * Runs {@code work} if nothing else holds the lock.
     *
     * @throws PipelineAlreadyRunningException if something does
     */
    public <T> T exclusively(Supplier<T> work) {
        if (!permit.tryAcquire()) {
            throw new PipelineAlreadyRunningException();
        }
        try {
            return work.get();
        } finally {
            permit.release();
        }
    }
}
