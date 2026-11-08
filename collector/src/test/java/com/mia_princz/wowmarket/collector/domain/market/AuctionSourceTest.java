package com.mia_princz.wowmarket.collector.domain.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuctionSourceTest {

    @Test
    void readsBackEveryKey() {
        AuctionSource commodities = new AuctionSource.RegionCommodities();
        AuctionSource draenor = new AuctionSource.ConnectedRealm(1403);

        assertThat(AuctionSource.fromKey(commodities.key())).isEqualTo(commodities);
        assertThat(AuctionSource.fromKey(draenor.key())).isEqualTo(draenor);
    }

    @Test
    void refusesAnythingElse() {
        assertThatThrownBy(() -> AuctionSource.fromKey("connected-realm-x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuctionSource.fromKey("connected-realm-0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuctionSource.fromKey("realm-1403")).isInstanceOf(IllegalArgumentException.class);
    }
}
