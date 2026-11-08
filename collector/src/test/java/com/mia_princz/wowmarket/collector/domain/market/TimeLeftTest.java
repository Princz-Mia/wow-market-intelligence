package com.mia_princz.wowmarket.collector.domain.market;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TimeLeftTest {

    @Test
    void readsEveryKnownBucket() {
        assertThat(TimeLeft.fromApi("SHORT")).contains(TimeLeft.SHORT);
        assertThat(TimeLeft.fromApi("VERY_LONG")).contains(TimeLeft.VERY_LONG);
    }

    @Test
    void reportsAnUnknownOrMissingValueAsEmpty() {
        assertThat(TimeLeft.fromApi("FOREVER")).isEmpty();
        assertThat(TimeLeft.fromApi("short")).isEmpty();
        assertThat(TimeLeft.fromApi(null)).isEmpty();
    }
}
