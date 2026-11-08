package com.mia_princz.wowmarket.collector.application.port.out;

import java.time.LocalDate;
import java.util.List;

/** Removes staged listings that have aged out, a whole day at a time. */
public interface StagingRetentionPort {

    /**
     * Drops the staged listings of every UTC day before {@code firstKeptDay}.
     *
     * @return the days dropped, oldest first
     */
    List<LocalDate> dropDaysBefore(LocalDate firstKeptDay);
}
