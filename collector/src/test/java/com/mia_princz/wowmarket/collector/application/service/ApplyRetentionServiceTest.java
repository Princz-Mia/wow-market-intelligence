package com.mia_princz.wowmarket.collector.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mia_princz.wowmarket.collector.application.port.out.RawArchivePort;
import com.mia_princz.wowmarket.collector.application.port.out.StagingRetentionPort;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.pipeline.RetentionSummary;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApplyRetentionServiceTest {

    private final List<String> calls = new ArrayList<>();

    private final StagingRetentionPort staging = firstKeptDay -> {
        calls.add("staging<" + firstKeptDay);
        return List.of(firstKeptDay.minusDays(1));
    };

    private final RawArchivePort archive = new RawArchivePort() {
        @Override
        public ArchivedSnapshot store(
                String region, AuctionSource source, Instant lastModified, InputStream body, boolean gzipped) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream open(String location) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteDaysBefore(String region, LocalDate firstKeptDay) {
            calls.add("archive:" + region + "<" + firstKeptDay);
            return 9;
        }
    };

    @Test
    void keepsTheConfiguredFullDaysBesidesToday() {
        // Just after midnight UTC: today is the 8th even though Budapest is an hour ahead.
        Clock clock = Clock.fixed(Instant.parse("2026-11-08T00:05:00Z"), ZoneOffset.UTC);

        RetentionSummary summary = new ApplyRetentionService("eu", 3, 30, staging, archive, clock).apply();

        assertThat(calls).containsExactly("staging<2026-11-05", "archive:eu<2026-10-09");
        assertThat(summary).isEqualTo(new RetentionSummary(List.of(LocalDate.parse("2026-11-04")), 9));
    }

    @Test
    void refusesAnArchiveThatWouldExpireBeforeStaging() {
        assertThatThrownBy(() -> new ApplyRetentionService("eu", 7, 3, staging, archive, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
