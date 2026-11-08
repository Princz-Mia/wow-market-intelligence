package com.mia_princz.wowmarket.collector.application.port.in;

import com.mia_princz.wowmarket.collector.domain.pipeline.RetentionSummary;

/** Removes staged listings and archived files older than their configured retention. */
public interface ApplyRetentionUseCase {

    RetentionSummary apply();
}
