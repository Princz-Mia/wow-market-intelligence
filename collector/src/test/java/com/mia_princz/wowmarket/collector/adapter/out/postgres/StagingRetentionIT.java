package com.mia_princz.wowmarket.collector.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.mia_princz.wowmarket.collector.TestcontainersConfiguration;
import com.mia_princz.wowmarket.collector.application.port.out.StagingRetentionPort;
import com.mia_princz.wowmarket.collector.application.port.out.StagingWriterPort;
import com.mia_princz.wowmarket.collector.application.port.out.StagingWriterPort.StagingBatch;
import com.mia_princz.wowmarket.collector.domain.market.AuctionListing;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.market.ItemVariantKey;
import com.mia_princz.wowmarket.collector.domain.market.ListedItem;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class StagingRetentionIT {

    private static final AuctionListing ORE =
            new AuctionListing(2000001L, ListedItem.commodity(190396), 200, 1_250_000L, null, null, "SHORT");

    @Autowired
    StagingWriterPort staging;

    @Autowired
    StagingRetentionPort retention;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void dropAllPartitions() {
        retention.dropDaysBefore(LocalDate.parse("2100-01-01"));
    }

    private void stage(long snapshotId, String eventTime) {
        try (StagingBatch batch =
                staging.open(snapshotId, new AuctionSource.RegionCommodities(), Instant.parse(eventTime))) {
            batch.add(ORE, ItemVariantKey.of(ORE.item()));
            batch.commit();
        }
    }

    @Test
    void dropsWholeDaysBeforeTheFirstKeptDay() {
        stage(1, "2026-11-04T23:21:53Z");
        stage(2, "2026-11-05T00:21:53Z");
        stage(3, "2026-11-08T09:21:53Z");

        assertThat(retention.dropDaysBefore(LocalDate.parse("2026-11-05")))
                .containsExactly(LocalDate.parse("2026-11-04"));

        assertThat(jdbc.sql("SELECT snapshot_id FROM raw.auction_listing ORDER BY snapshot_id")
                        .query(Long.class)
                        .list())
                .containsExactly(2L, 3L);
        assertThat(jdbc.sql("SELECT to_regclass('raw.auction_listing_20261104') IS NULL")
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }

    @Test
    void dropsNothingWhenEveryDayIsKept() {
        stage(1, "2026-11-08T09:21:53Z");

        assertThat(retention.dropDaysBefore(LocalDate.parse("2026-11-05"))).isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM raw.auction_listing")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }
}
