package com.mia_princz.wowmarket.collector.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

class CollectorPropertiesTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(CollectorConfiguration.class)
            .withPropertyValues(
                    "collector.region=eu",
                    "collector.connected-realms=1403,1080",
                    "collector.capture.enabled=true",
                    "collector.capture.cron=0 10,40 * * * *",
                    "collector.capture.concurrency=4",
                    "collector.capture.max-run-age=2h",
                    "collector.archive.root=data/archive",
                    "collector.load.batch-size=100",
                    "collector.retention.staged-days=3",
                    "collector.retention.archive-days=30",
                    "collector.blizzard.client-id=id",
                    "collector.blizzard.client-secret=very-secret",
                    "collector.blizzard.token-url=https://oauth.battle.net/token",
                    "collector.blizzard.api-url=https://eu.api.blizzard.com",
                    "collector.blizzard.requests-per-second=50",
                    "collector.blizzard.connect-timeout=10s",
                    "collector.blizzard.request-timeout=2m",
                    "collector.blizzard.max-attempts=4");

    @Test
    void bindsAValidConfiguration() {
        context.run(app -> {
            CollectorProperties properties = app.getBean(CollectorProperties.class);
            assertThat(properties.connectedRealms()).containsExactly(1403, 1080);
            assertThat(properties.archive().root()).isEqualTo(Path.of("data/archive"));
            assertThat(properties.load().batchSize()).isEqualTo(100);
            assertThat(properties.blizzard().requestTimeout()).isEqualTo(Duration.ofMinutes(2));
        });
    }

    @Test
    void refusesToStartWithoutCredentials() {
        context.withPropertyValues("collector.blizzard.client-secret=")
                .run(app -> assertThat(app)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("collector.blizzard.clientSecret"));
    }

    @Test
    void refusesAnUnknownRegion() {
        context.withPropertyValues("collector.region=cn")
                .run(app -> assertThat(app).hasFailed().getFailure().hasStackTraceContaining("collector.region"));
    }

    @Test
    void refusesARealmListedTwice() {
        context.withPropertyValues("collector.connected-realms=1403,1403")
                .run(app -> assertThat(app).hasFailed().getFailure().hasStackTraceContaining("lists a realm twice"));
    }

    @Test
    void refusesARequestRateAboveTheBlizzardLimit() {
        context.withPropertyValues("collector.blizzard.requests-per-second=101")
                .run(app -> assertThat(app)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("collector.blizzard.requestsPerSecond"));
    }

    @Test
    void neverPrintsTheClientSecret() {
        context.run(app -> assertThat(app.getBean(CollectorProperties.class).toString())
                .doesNotContain("very-secret")
                .contains("clientSecret=***"));
    }
}
