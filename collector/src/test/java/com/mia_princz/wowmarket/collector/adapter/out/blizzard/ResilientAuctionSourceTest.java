package com.mia_princz.wowmarket.collector.adapter.out.blizzard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mia_princz.wowmarket.collector.application.port.out.AuctionSourcePort;
import com.mia_princz.wowmarket.collector.application.port.out.FetchResult;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ResilientAuctionSourceTest {

    private static final AuctionSource COMMODITIES = new AuctionSource.RegionCommodities();
    private static final Duration FAST = Duration.ofMillis(1);

    /** Answers with the queued outcomes in order, counting calls. */
    private static final class ScriptedSource implements AuctionSourcePort {
        private final Deque<RuntimeException> failures = new ArrayDeque<>();
        int calls;

        ScriptedSource failingWith(RuntimeException... queued) {
            failures.addAll(java.util.List.of(queued));
            return this;
        }

        @Override
        public FetchResult fetch(AuctionSource source, Optional<Instant> modifiedSince) {
            calls++;
            if (!failures.isEmpty()) {
                throw failures.poll();
            }
            return new FetchResult.NotModified();
        }
    }

    private static RateLimiter unlimited() {
        return RateLimiter.of(
                "test",
                RateLimiterConfig.custom()
                        .limitForPeriod(1000)
                        .limitRefreshPeriod(Duration.ofSeconds(1))
                        .build());
    }

    private static BlizzardApiException http(int status) {
        return new BlizzardApiException("HTTP " + status, status, null);
    }

    @Test
    void retriesServerErrorsAndTimeoutsUntilAnAnswer() {
        var source = new ScriptedSource()
                .failingWith(http(503), new BlizzardApiException("timed out", new java.io.IOException()), http(429));

        FetchResult result =
                new ResilientAuctionSource(source, unlimited(), 4, FAST).fetch(COMMODITIES, Optional.empty());

        assertThat(result).isInstanceOf(FetchResult.NotModified.class);
        assertThat(source.calls).isEqualTo(4);
    }

    @Test
    void givesUpAfterTheLastAttempt() {
        var source = new ScriptedSource().failingWith(http(500), http(500), http(500));

        assertThatThrownBy(() ->
                        new ResilientAuctionSource(source, unlimited(), 3, FAST).fetch(COMMODITIES, Optional.empty()))
                .hasMessage("HTTP 500");
        assertThat(source.calls).isEqualTo(3);
    }

    @Test
    void doesNotRetryAnswersThatWillNotChange() {
        var source = new ScriptedSource().failingWith(http(404));

        assertThatThrownBy(() ->
                        new ResilientAuctionSource(source, unlimited(), 4, FAST).fetch(COMMODITIES, Optional.empty()))
                .hasMessage("HTTP 404");
        assertThat(source.calls).isEqualTo(1);
    }

    @Test
    void waitsAsLongAsRetryAfterSaysUpToAMinute() {
        var asked = new BlizzardApiException("HTTP 429", 429, Duration.ofSeconds(7));
        var tooLong = new BlizzardApiException("HTTP 429", 429, Duration.ofHours(1));

        assertThat(ResilientAuctionSource.waitBefore(1, asked, Duration.ofSeconds(1)))
                .isEqualTo(Duration.ofSeconds(7));
        assertThat(ResilientAuctionSource.waitBefore(1, tooLong, Duration.ofSeconds(1)))
                .isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void backsOffExponentiallyWithJitterAndACap() {
        Duration base = Duration.ofSeconds(1);

        assertThat(ResilientAuctionSource.waitBefore(1, http(503), base)).isBetween(base, Duration.ofMillis(1500));
        assertThat(ResilientAuctionSource.waitBefore(3, http(503), base))
                .isBetween(Duration.ofSeconds(4), Duration.ofSeconds(6));
        assertThat(ResilientAuctionSource.waitBefore(20, http(503), base))
                .isBetween(Duration.ofSeconds(30), Duration.ofSeconds(45));
    }

    @Test
    void takesAPermitFromTheRateLimiterForEveryAttempt() {
        RateLimiter onePerSecond = RateLimiter.of(
                "strict",
                RateLimiterConfig.custom()
                        .limitForPeriod(1)
                        .limitRefreshPeriod(Duration.ofSeconds(10))
                        .timeoutDuration(Duration.ZERO)
                        .build());
        var resilient = new ResilientAuctionSource(new ScriptedSource(), onePerSecond, 1, FAST);

        resilient.fetch(COMMODITIES, Optional.empty());

        assertThatThrownBy(() -> resilient.fetch(COMMODITIES, Optional.empty()))
                .isInstanceOf(RequestNotPermitted.class);
    }
}
