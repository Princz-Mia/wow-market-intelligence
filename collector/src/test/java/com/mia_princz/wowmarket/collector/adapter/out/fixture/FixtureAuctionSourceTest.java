package com.mia_princz.wowmarket.collector.adapter.out.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mia_princz.wowmarket.collector.application.port.out.FetchResult;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FixtureAuctionSourceTest {

    private static final AuctionSource COMMODITIES = new AuctionSource.RegionCommodities();
    private final FixtureAuctionSource fixtures =
            new FixtureAuctionSource(Clock.fixed(Instant.parse("2026-11-08T10:42:00Z"), ZoneOffset.UTC));

    @Test
    void findsTheBundledSources() {
        assertThat(fixtures.sources()).containsExactly("commodities", "connected-realm-1305", "connected-realm-1403");
    }

    @Test
    void servesTheSnapshotsInOrderEndingInTheCurrentHourThenNothingNew() throws IOException {
        FetchResult first = fixtures.fetch(COMMODITIES, Optional.empty());
        assertThat(first).isInstanceOfSatisfying(FetchResult.Modified.class, snapshot -> {
            assertThat(snapshot.lastModified()).isEqualTo(Instant.parse("2026-11-08T09:21:53Z"));
            assertThat(snapshot.gzipped()).isFalse();
        });
        ((FetchResult.Modified) first).close();

        FetchResult second = fixtures.fetch(COMMODITIES, Optional.of(Instant.parse("2026-11-08T09:21:53Z")));
        assertThat(second).isInstanceOfSatisfying(FetchResult.Modified.class, snapshot -> {
            assertThat(snapshot.lastModified()).isEqualTo(Instant.parse("2026-11-08T10:21:53Z"));
            try (snapshot) {
                assertThat(new String(snapshot.body().readAllBytes(), StandardCharsets.UTF_8))
                        .contains("\"auctions\"");
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        });

        assertThat(fixtures.fetch(COMMODITIES, Optional.of(Instant.parse("2026-11-08T10:21:53Z"))))
                .isInstanceOf(FetchResult.NotModified.class);
    }

    @Test
    void refusesASourceWithoutFixtures() {
        assertThatThrownBy(() -> fixtures.fetch(new AuctionSource.ConnectedRealm(1080), Optional.empty()))
                .hasMessage("no offline fixtures for connected-realm-1080");
    }
}
