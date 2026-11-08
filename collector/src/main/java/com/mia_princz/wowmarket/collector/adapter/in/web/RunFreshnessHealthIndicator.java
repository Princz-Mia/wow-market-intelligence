package com.mia_princz.wowmarket.collector.adapter.in.web;

import com.mia_princz.wowmarket.collector.application.port.in.PipelineStatusUseCase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Reports the collector down when no run has completed for longer than the allowed age. Runs are due every half
 * hour, so a few missed in a row mean collection has stopped, and every hour lost cannot be fetched later. The
 * container's health check then turns unhealthy, which {@code docker compose ps} shows.
 */
public class RunFreshnessHealthIndicator implements HealthIndicator {

    private final PipelineStatusUseCase status;
    private final Clock clock;
    private final Duration maxAge;

    public RunFreshnessHealthIndicator(PipelineStatusUseCase status, Clock clock, Duration maxAge) {
        this.status = status;
        this.clock = clock;
        this.maxAge = maxAge;
    }

    @Override
    public Health health() {
        Optional<Instant> last = status.lastCompletedRun();
        if (last.isEmpty()) {
            // A fresh install: the startup run is about to complete.
            return Health.up().withDetail("lastCompletedRun", "none yet").build();
        }
        Duration age = Duration.between(last.get(), clock.instant());
        Health.Builder health = age.compareTo(maxAge) > 0 ? Health.down() : Health.up();
        return health.withDetail("lastCompletedRun", last.get().toString())
                .withDetail("ageMinutes", age.toMinutes())
                .withDetail("maxAgeMinutes", maxAge.toMinutes())
                .build();
    }
}
