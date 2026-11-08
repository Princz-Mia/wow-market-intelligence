package com.mia_princz.wowmarket.collector.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mia_princz.wowmarket.collector.application.port.out.AuctionPayloadParserPort;
import com.mia_princz.wowmarket.collector.application.port.out.AuctionPayloadParserPort.MalformedPayloadException;
import com.mia_princz.wowmarket.collector.application.port.out.RawArchivePort;
import com.mia_princz.wowmarket.collector.application.port.out.RawArchivePort.MissingSnapshotFileException;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotLoadRegistryPort;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotLoadRegistryPort.LoadRecord;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotLoadRegistryPort.PendingSnapshot;
import com.mia_princz.wowmarket.collector.application.port.out.StagingWriterPort;
import com.mia_princz.wowmarket.collector.domain.market.AuctionListing;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.market.ItemVariantKey;
import com.mia_princz.wowmarket.collector.domain.market.ListedItem;
import com.mia_princz.wowmarket.collector.domain.pipeline.LoadSummary;
import com.mia_princz.wowmarket.collector.domain.quality.ValidationContext.History;
import com.mia_princz.wowmarket.collector.domain.quality.ValidationResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LoadSnapshotsServiceTest {

    private static final AuctionSource COMMODITIES = new AuctionSource.RegionCommodities();
    private static final AuctionSource DRAENOR = new AuctionSource.ConnectedRealm(1403);
    private static final Instant T1 = Instant.parse("2026-11-07T19:21:53Z");
    private static final Instant T2 = Instant.parse("2026-11-07T20:21:53Z");

    /** Listings each archive location holds; a location missing here is missing from the archive. */
    private final Map<String, List<AuctionListing>> archived = new HashMap<>();

    private final List<PendingSnapshot> pending = new ArrayList<>();
    private final List<LoadRecord> recorded = new ArrayList<>();
    private final Map<Long, List<Long>> committed = new HashMap<>();

    private final RawArchivePort archive = new RawArchivePort() {
        @Override
        public ArchivedSnapshot store(
                String region, AuctionSource source, Instant lastModified, InputStream body, boolean gzipped) {
            throw new UnsupportedOperationException("loading never stores");
        }

        @Override
        public InputStream open(String location) {
            if (location.startsWith("unreachable")) {
                throw new UncheckedIOException(new IOException("input/output error"));
            }
            if (!archived.containsKey(location)) {
                throw new MissingSnapshotFileException(location, null);
            }
            return new ByteArrayInputStream(location.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public int deleteDaysBefore(String region, LocalDate firstKeptDay) {
            throw new UnsupportedOperationException("loading never deletes");
        }
    };

    /** Reads the location name the fake archive streams, and replays that location's listings. */
    private final AuctionPayloadParserPort parser = (json, listings) -> {
        try {
            String location = new String(json.readAllBytes(), StandardCharsets.UTF_8);
            if (location.endsWith(".broken")) {
                throw new MalformedPayloadException("the body has no auctions array", null);
            }
            archived.get(location).forEach(listings);
            return archived.get(location).size();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    };

    private final StagingWriterPort staging =
            (snapshotId, source, lastModified) -> new StagingWriterPort.StagingBatch() {
                private final List<Long> rows = new ArrayList<>();

                @Override
                public void add(AuctionListing listing, ItemVariantKey variant) {
                    rows.add(listing.id());
                }

                @Override
                public long commit() {
                    committed.put(snapshotId, List.copyOf(rows));
                    return rows.size();
                }

                @Override
                public void close() {}
            };

    private final SnapshotLoadRegistryPort registry = new SnapshotLoadRegistryPort() {
        @Override
        public List<PendingSnapshot> pending(String region, int limit) {
            return pending.stream().limit(limit).toList();
        }

        @Override
        public Optional<Instant> lastLoaded(String region, AuctionSource source) {
            return Optional.empty();
        }

        @Override
        public History history(String region, AuctionSource source, Instant eventTime) {
            return History.NONE;
        }

        @Override
        public void recordLoad(LoadRecord load) {
            recorded.add(load);
        }
    };

    private final LoadSnapshotsService service = new LoadSnapshotsService(
            "eu", 10, archive, parser, staging, registry, Clock.fixed(T2.plusSeconds(900), ZoneOffset.UTC));

    private static AuctionListing ore(long id, Integer quantity) {
        return new AuctionListing(id, ListedItem.commodity(190396), quantity, 1_250_000L, null, null, "SHORT");
    }

    private void waiting(long id, AuctionSource source, Instant lastModified, String location) {
        pending.add(new PendingSnapshot(id, source, lastModified, location));
    }

    @Test
    void stagesTheListingsThatPassAndRecordsTheVerdict() {
        // One listing without a quantity among 2001 stays under the 0.1% limit: dropped with a warning.
        List<AuctionListing> listings = new ArrayList<>();
        for (long id = 1; id <= 2000; id++) {
            listings.add(ore(id, 5));
        }
        listings.add(ore(2001, null));
        archived.put("c1", listings);
        waiting(11, COMMODITIES, T2, "c1");

        LoadSummary summary = service.loadPending();

        assertThat(summary).isEqualTo(new LoadSummary(1, 0, 0, 2000));
        assertThat(committed.get(11L)).hasSize(2000).doesNotContain(2001L);
        LoadRecord record = recorded.getFirst();
        assertThat(record.loaded()).isTrue();
        assertThat(record.listingsRead()).isEqualTo(2001);
        assertThat(record.listingsStaged()).isEqualTo(2000);
        assertThat(record.distinctItems()).isEqualTo(1);
        assertThat(record.results())
                .filteredOn(result -> result.ruleId().equals("DQ-I04"))
                .singleElement()
                .isInstanceOf(ValidationResult.Warned.class);
    }

    @Test
    void quarantinesASnapshotThatFailsABlockingCheck() {
        archived.put("c1", List.of(ore(1, 200), ore(1, 5)));
        waiting(11, COMMODITIES, T2, "c1");

        LoadSummary summary = service.loadPending();

        assertThat(summary).isEqualTo(new LoadSummary(0, 1, 0, 0));
        assertThat(committed).isEmpty();
        assertThat(recorded.getFirst().loaded()).isFalse();
        assertThat(recorded.getFirst().results())
                .contains(new ValidationResult.Failed("DQ-I06", "1 duplicate auction ids"));
    }

    @Test
    void quarantinesAnUnreadablePayload() {
        archived.put("c1.broken", List.of());
        waiting(11, COMMODITIES, T2, "c1.broken");

        service.loadPending();

        assertThat(recorded)
                .singleElement()
                .isEqualTo(new LoadRecord(
                        11,
                        false,
                        null,
                        0,
                        null,
                        T2.plusSeconds(900),
                        List.of(new ValidationResult.Failed("DQ-I01", "the body has no auctions array"))));
    }

    @Test
    void holdsBackASourceAfterAFailureAndCarriesOnWithTheOthers() {
        archived.put("c1", List.of(ore(1, 200)));
        archived.put("d2", List.of(new AuctionListing(9L, ListedItem.commodity(2589), 1, null, null, 120L, "SHORT")));
        waiting(21, DRAENOR, T1, "unreachable");
        waiting(11, COMMODITIES, T2, "c1");
        waiting(22, DRAENOR, T2, "d2");

        LoadSummary summary = service.loadPending();

        assertThat(summary).isEqualTo(new LoadSummary(1, 0, 1, 1));
        assertThat(recorded).extracting(LoadRecord::snapshotId).containsExactly(11L);
    }

    @Test
    void quarantinesASnapshotWhoseFileIsGoneSoTheSourceKeepsLoading() {
        archived.put("d2", List.of(new AuctionListing(9L, ListedItem.commodity(2589), 1, null, null, 120L, "SHORT")));
        waiting(21, DRAENOR, T1, "gone");
        waiting(22, DRAENOR, T2, "d2");

        LoadSummary summary = service.loadPending();

        assertThat(summary).isEqualTo(new LoadSummary(1, 1, 0, 1));
        assertThat(recorded.getFirst().results())
                .containsExactly(new ValidationResult.Failed("DQ-I01", "archive file missing: gone"));
    }
}
