package com.mia_princz.wowmarket.collector.adapter.out.fixture;

import com.mia_princz.wowmarket.collector.application.port.out.AuctionSourcePort;
import com.mia_princz.wowmarket.collector.application.port.out.FetchResult;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Serves bundled snapshots instead of the Blizzard API, for development without credentials, CI and a demo fallback.
 * Fixtures live at {@code offline/{source key}/{n}.json}, numbered in time order. They are given consecutive hourly
 * event times ending in the current hour, so retention keeps them and each restart presents them as fresh snapshots.
 * Like the real API, a source answers "not modified" once its newest fixture has been fetched.
 */
public class FixtureAuctionSource implements AuctionSourcePort {

    static final String FIXTURES = "classpath*:offline/*/*.json";
    private static final Duration PUBLISHED_AFTER_THE_HOUR =
            Duration.ofMinutes(21).plusSeconds(53);

    private final Map<String, List<Resource>> fixtures;
    private final Instant firstEventTime;

    public FixtureAuctionSource(Clock clock) {
        this.fixtures = load();
        int snapshots = fixtures.values().stream().mapToInt(List::size).max().orElse(0);
        this.firstEventTime = clock.instant()
                .truncatedTo(ChronoUnit.HOURS)
                .minus(Duration.ofHours(Math.max(snapshots - 1, 0)))
                .plus(PUBLISHED_AFTER_THE_HOUR);
    }

    /** The source keys fixtures exist for. */
    public List<String> sources() {
        return fixtures.keySet().stream().sorted().toList();
    }

    @Override
    public FetchResult fetch(AuctionSource source, Optional<Instant> modifiedSince) {
        List<Resource> snapshots = fixtures.get(source.key());
        if (snapshots == null) {
            throw new IllegalStateException("no offline fixtures for " + source.key());
        }
        for (int i = 0; i < snapshots.size(); i++) {
            Instant eventTime = firstEventTime.plus(Duration.ofHours(i));
            if (modifiedSince.isEmpty() || eventTime.isAfter(modifiedSince.get())) {
                try {
                    return new FetchResult.Modified(eventTime, snapshots.get(i).getInputStream(), false);
                } catch (IOException e) {
                    throw new UncheckedIOException("could not read fixture " + snapshots.get(i), e);
                }
            }
        }
        return new FetchResult.NotModified();
    }

    private static Map<String, List<Resource>> load() {
        try {
            Resource[] found = new PathMatchingResourcePatternResolver().getResources(FIXTURES);
            return Arrays.stream(found)
                    .collect(Collectors.groupingBy(
                            FixtureAuctionSource::sourceKey,
                            Collectors.collectingAndThen(
                                    Collectors.toList(),
                                    list -> list.stream()
                                            .sorted(Comparator.comparingInt(FixtureAuctionSource::number))
                                            .toList())));
        } catch (IOException e) {
            throw new UncheckedIOException("could not list the offline fixtures", e);
        }
    }

    private static String sourceKey(Resource fixture) {
        String[] path = path(fixture);
        return path[path.length - 2];
    }

    private static int number(Resource fixture) {
        String[] path = path(fixture);
        return Integer.parseInt(path[path.length - 1].replace(".json", ""));
    }

    private static String[] path(Resource fixture) {
        try {
            return fixture.getURL().toString().split("/");
        } catch (IOException e) {
            throw new UncheckedIOException("could not locate fixture " + fixture, e);
        }
    }
}
