package com.mia_princz.wowmarket.collector.application.service;

import com.mia_princz.wowmarket.collector.application.port.in.ApplyRetentionUseCase;
import com.mia_princz.wowmarket.collector.application.port.out.RawArchivePort;
import com.mia_princz.wowmarket.collector.application.port.out.StagingRetentionPort;
import com.mia_princz.wowmarket.collector.domain.pipeline.RetentionSummary;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Keeps the last {@code days} full UTC days plus today. With three days of staging there are always at least 72 hours
 * of consecutive snapshots for the listing lifecycle, and everything older can be replayed from the archive for as
 * long as the archive keeps it.
 */
public class ApplyRetentionService implements ApplyRetentionUseCase {

    private static final System.Logger LOG = System.getLogger(ApplyRetentionService.class.getName());

    private final String region;
    private final int stagedDays;
    private final int archiveDays;
    private final StagingRetentionPort staging;
    private final RawArchivePort archive;
    private final Clock clock;

    public ApplyRetentionService(
            String region,
            int stagedDays,
            int archiveDays,
            StagingRetentionPort staging,
            RawArchivePort archive,
            Clock clock) {
        if (archiveDays < stagedDays) {
            throw new IllegalArgumentException(
                    "the archive must outlive staging, or staged days could not be replayed");
        }
        this.region = region;
        this.stagedDays = stagedDays;
        this.archiveDays = archiveDays;
        this.staging = staging;
        this.archive = archive;
        this.clock = clock;
    }

    @Override
    public RetentionSummary apply() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<LocalDate> dropped = staging.dropDaysBefore(today.minusDays(stagedDays));
        int deleted = archive.deleteDaysBefore(region, today.minusDays(archiveDays));
        if (!dropped.isEmpty() || deleted > 0) {
            LOG.log(Level.INFO, () -> "retention staged_days_dropped=" + dropped + " archive_days_deleted=" + deleted);
        }
        return new RetentionSummary(dropped, deleted);
    }
}
