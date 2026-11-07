package com.mia_princz.wowmarket.collector.adapter.out.blizzard;

import java.time.Duration;
import java.util.Optional;

/** A Blizzard API call that did not produce a usable response. Never carries credentials or tokens. */
public class BlizzardApiException extends RuntimeException {

    private final int status;
    private final Duration retryAfter;

    public BlizzardApiException(String message, int status, Duration retryAfter) {
        super(message);
        this.status = status;
        this.retryAfter = retryAfter;
    }

    public BlizzardApiException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
        this.retryAfter = null;
    }

    /** HTTP status, or 0 when no response arrived. */
    public int status() {
        return status;
    }

    /** How long Blizzard asked us to wait, when it said so. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
