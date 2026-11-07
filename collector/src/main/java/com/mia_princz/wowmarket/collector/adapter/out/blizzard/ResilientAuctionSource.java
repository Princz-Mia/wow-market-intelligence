package com.mia_princz.wowmarket.collector.adapter.out.blizzard;

import com.mia_princz.wowmarket.collector.application.port.out.AuctionSourcePort;
import com.mia_princz.wowmarket.collector.application.port.out.FetchResult;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps requests below the Blizzard rate limit and retries the failures that are worth retrying: no response,
 * a refused token (already dropped by the client), 429 and 5xx. Waits as long as Retry-After says, otherwise backs off
 * exponentially with jitter. Other answers, such as 403 or 404, fail at once.
 */
public class ResilientAuctionSource implements AuctionSourcePort {

    static final Duration MAX_BACKOFF = Duration.ofSeconds(30);
    static final Duration MAX_RETRY_AFTER = Duration.ofMinutes(1);

    private final AuctionSourcePort delegate;
    private final RateLimiter rateLimiter;
    private final Retry retry;

    public ResilientAuctionSource(
            AuctionSourcePort delegate, RateLimiter rateLimiter, int maxAttempts, Duration baseBackoff) {
        this.delegate = delegate;
        this.rateLimiter = rateLimiter;
        this.retry = Retry.of(
                "blizzard-auctions",
                RetryConfig.<FetchResult>custom()
                        .maxAttempts(maxAttempts)
                        .retryOnException(ResilientAuctionSource::isRetryable)
                        .intervalBiFunction((attempt, outcome) -> waitBefore(attempt, outcome.getLeft(), baseBackoff)
                                .toMillis())
                        .build());
    }

    @Override
    public FetchResult fetch(AuctionSource source, Optional<Instant> modifiedSince) {
        return retry.executeSupplier(() -> {
            RateLimiter.waitForPermission(rateLimiter);
            return delegate.fetch(source, modifiedSince);
        });
    }

    static boolean isRetryable(Throwable failure) {
        if (!(failure instanceof BlizzardApiException api)) {
            return false;
        }
        int status = api.status();
        return status == 0 || status == 401 || status == 429 || status >= 500;
    }

    /** How long to wait before the next attempt; {@code attempt} counts the attempts made so far, from 1. */
    static Duration waitBefore(int attempt, Throwable failure, Duration baseBackoff) {
        if (failure instanceof BlizzardApiException api && api.retryAfter().isPresent()) {
            Duration asked = api.retryAfter().get();
            return asked.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : asked;
        }
        Duration exponential = baseBackoff.multipliedBy(1L << Math.min(attempt - 1, 10));
        Duration capped = exponential.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : exponential;
        long jitterMillis = ThreadLocalRandom.current().nextLong(capped.toMillis() / 2 + 1);
        return capped.plusMillis(jitterMillis);
    }
}
