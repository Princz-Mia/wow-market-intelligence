package com.mia_princz.wowmarket.collector.domain.market;

import java.util.Optional;

/** How long a listing has left, as the API reports it in buckets instead of a timestamp. */
public enum TimeLeft {
    SHORT,
    MEDIUM,
    LONG,
    VERY_LONG;

    /** The bucket for an API value, or empty for a value this version does not know. */
    public static Optional<TimeLeft> fromApi(String value) {
        for (TimeLeft bucket : values()) {
            if (bucket.name().equals(value)) {
                return Optional.of(bucket);
            }
        }
        return Optional.empty();
    }
}
