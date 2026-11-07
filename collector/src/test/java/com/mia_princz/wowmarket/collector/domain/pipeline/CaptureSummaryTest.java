package com.mia_princz.wowmarket.collector.domain.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CaptureSummaryTest {

    @Test
    void succeedsWhenEverySourceAnswered() {
        assertThat(new CaptureSummary(9, 2, 7, List.of()).status()).isEqualTo(RunStatus.SUCCEEDED);
    }

    @Test
    void isPartialWhenSomeSourcesFailed() {
        assertThat(new CaptureSummary(9, 8, 0, List.of("x")).status()).isEqualTo(RunStatus.PARTIAL);
    }

    @Test
    void failsWhenNoSourceAnswered() {
        assertThat(new CaptureSummary(2, 0, 0, List.of("x", "y")).status()).isEqualTo(RunStatus.FAILED);
    }

    @Test
    void refusesCountsThatDoNotAddUp() {
        assertThatThrownBy(() -> new CaptureSummary(9, 2, 2, List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
