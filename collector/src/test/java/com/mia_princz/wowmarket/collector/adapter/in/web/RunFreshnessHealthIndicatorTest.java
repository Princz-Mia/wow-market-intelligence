package com.mia_princz.wowmarket.collector.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.mia_princz.wowmarket.collector.application.port.in.PipelineStatusUseCase;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class RunFreshnessHealthIndicatorTest {

    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

    private static Health health(Optional<Instant> lastCompleted) {
        PipelineStatusUseCase status = new PipelineStatusUseCase() {
            @Override
            public Optional<RunView> latestRun() {
                return Optional.empty();
            }

            @Override
            public List<SnapshotView> recentSnapshots(Optional<AuctionSource> source, int limit) {
                return List.of();
            }

            @Override
            public Optional<Instant> lastCompletedRun() {
                return lastCompleted;
            }
        };
        return new RunFreshnessHealthIndicator(status, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(2)).health();
    }

    @Test
    void isUpBeforeTheFirstRunCompletes() {
        assertThat(health(Optional.empty()).getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void isUpWhileTheLastRunIsRecent() {
        Health health = health(Optional.of(NOW.minus(Duration.ofMinutes(35))));

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("ageMinutes", 35L).containsEntry("maxAgeMinutes", 120L);
    }

    @Test
    void isDownOnceRunsHaveStoppedForLongerThanAllowed() {
        assertThat(health(Optional.of(NOW.minus(Duration.ofMinutes(121)))).getStatus())
                .isEqualTo(Status.DOWN);
    }
}
