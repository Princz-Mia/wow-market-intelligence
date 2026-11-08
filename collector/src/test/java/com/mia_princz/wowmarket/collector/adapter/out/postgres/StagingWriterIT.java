package com.mia_princz.wowmarket.collector.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.mia_princz.wowmarket.collector.TestcontainersConfiguration;
import com.mia_princz.wowmarket.collector.application.port.out.StagingWriterPort;
import com.mia_princz.wowmarket.collector.application.port.out.StagingWriterPort.StagingBatch;
import com.mia_princz.wowmarket.collector.domain.market.AuctionListing;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.market.ItemVariantKey;
import com.mia_princz.wowmarket.collector.domain.market.ListedItem;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class StagingWriterIT {

    private static final AuctionSource COMMODITIES = new AuctionSource.RegionCommodities();
    private static final AuctionSource DRAENOR = new AuctionSource.ConnectedRealm(1403);
    private static final Instant EVENT_TIME = Instant.parse("2026-11-07T20:21:53Z");

    private static final AuctionListing ORE =
            new AuctionListing(2000001L, ListedItem.commodity(190396), 200, 1_250_000L, null, null, "SHORT");
    private static final AuctionListing SWORD = new AuctionListing(
            3000001L,
            new ListedItem(19019, 13, List.of(6646, 1472), null, null, null, null),
            1,
            null,
            45_000_000_000L,
            null,
            "MEDIUM");
    private static final AuctionListing PET = new AuctionListing(
            3000002L,
            new ListedItem(82800, null, List.of(), 1155, 25, 3, 5),
            1,
            null,
            2_000_000L,
            1_500_000L,
            "odd\tvalue\\");

    @Autowired
    StagingWriterPort staging;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PostgreSQLContainer postgres;

    @BeforeEach
    void emptyTable() {
        jdbc.sql("TRUNCATE raw.auction_listing").update();
    }

    private long stage(long snapshotId, AuctionSource source, Instant lastModified, AuctionListing... listings) {
        try (StagingBatch batch = staging.open(snapshotId, source, lastModified)) {
            for (AuctionListing listing : listings) {
                batch.add(listing, ItemVariantKey.of(listing.item()));
            }
            return batch.commit();
        }
    }

    private long rows(long snapshotId) {
        return jdbc.sql("SELECT count(*) FROM raw.auction_listing WHERE snapshot_id = ?")
                .param(snapshotId)
                .query(Long.class)
                .single();
    }

    @Test
    void stagesEveryFieldOfCommodityAndRealmListings() {
        assertThat(stage(1, COMMODITIES, EVENT_TIME, ORE)).isEqualTo(1);
        assertThat(stage(2, DRAENOR, EVENT_TIME, SWORD, PET)).isEqualTo(2);

        Map<String, Object> ore = row(2000001L);
        assertThat(ore)
                .containsEntry("connected_realm_id", null)
                .containsEntry("item_id", 190396)
                .containsEntry("item_variant_key", ItemVariantKey.of(ORE.item()).value())
                .containsEntry("quantity", 200)
                .containsEntry("unit_price", 1_250_000L)
                .containsEntry("buyout", null)
                .containsEntry("time_left", "SHORT")
                .containsEntry("bonus_lists", null);
        assertThat(row(3000001L))
                .containsEntry("connected_realm_id", 1403)
                .containsEntry("buyout", 45_000_000_000L)
                .containsEntry("item_context", 13)
                .containsEntry("bonus_lists", "{6646,1472}");
        assertThat(row(3000002L))
                .containsEntry("bid", 1_500_000L)
                .containsEntry("time_left", "odd\tvalue\\")
                .containsEntry("pet_species_id", 1155)
                .containsEntry("pet_level", 25)
                .containsEntry("pet_breed_id", 5);
    }

    @Test
    void replacesAnEarlierLoadOfTheSameSnapshot() {
        stage(2, DRAENOR, EVENT_TIME, SWORD, PET);

        stage(2, DRAENOR, EVENT_TIME, SWORD);

        assertThat(rows(2)).isEqualTo(1);
    }

    @Test
    void discardsABatchThatWasNotCommittedAndKeepsTheEarlierLoad() {
        stage(2, DRAENOR, EVENT_TIME, SWORD);

        try (StagingBatch batch = staging.open(2, DRAENOR, EVENT_TIME)) {
            batch.add(PET, ItemVariantKey.of(PET.item()));
        }

        assertThat(jdbc.sql("SELECT auction_id FROM raw.auction_listing WHERE snapshot_id = 2")
                        .query(Long.class)
                        .list())
                .containsExactly(3000001L);
    }

    @Test
    void putsEachUtcDayInItsOwnPartition() {
        stage(1, COMMODITIES, Instant.parse("2026-11-07T23:59:59Z"), ORE);
        stage(2, COMMODITIES, Instant.parse("2026-11-08T00:00:00Z"), ORE);

        assertThat(jdbc.sql("SELECT snapshot_id, tableoid::regclass::text AS partition FROM raw.auction_listing"
                                + " ORDER BY snapshot_id")
                        .query((rs, n) -> rs.getLong(1) + "=" + rs.getString(2))
                        .list())
                .containsExactly("1=raw.auction_listing_20261107", "2=raw.auction_listing_20261108");
    }

    @Test
    void transformerCanReadStagedListings() throws Exception {
        stage(1, COMMODITIES, Instant.parse("2026-11-09T10:21:53Z"), ORE);

        try (var transformer = DriverManager.getConnection(
                        TestcontainersConfiguration.warehouseUrl(postgres),
                        "transformer",
                        TestcontainersConfiguration.TRANSFORMER_PASSWORD);
                var result = transformer.createStatement().executeQuery("SELECT count(*) FROM raw.auction_listing")) {
            result.next();
            assertThat(result.getInt(1)).isEqualTo(1);
        }
    }

    private Map<String, Object> row(long auctionId) {
        return jdbc.sql("SELECT connected_realm_id, item_id, item_variant_key, quantity, unit_price, buyout, bid,"
                        + " time_left, item_context, bonus_lists::text AS bonus_lists, pet_species_id, pet_level,"
                        + " pet_breed_id FROM raw.auction_listing WHERE auction_id = ?")
                .param(auctionId)
                .query()
                .singleRow();
    }
}
