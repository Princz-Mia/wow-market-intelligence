package com.mia_princz.wowmarket.collector.domain.pipeline;

import java.time.LocalDate;
import java.util.List;

/**
 * What one retention step removed.
 *
 * @param stagedDaysDropped days of staged listings dropped, oldest first
 * @param archiveDaysDeleted source-days of archived snapshot files deleted
 */
public record RetentionSummary(List<LocalDate> stagedDaysDropped, int archiveDaysDeleted) {

    public RetentionSummary {
        stagedDaysDropped = List.copyOf(stagedDaysDropped);
    }
}
