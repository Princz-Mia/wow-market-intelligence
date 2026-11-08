package com.mia_princz.wowmarket.collector.config;

import com.mia_princz.wowmarket.collector.adapter.out.fixture.FixtureAuctionSource;
import com.mia_princz.wowmarket.collector.application.port.out.AuctionSourcePort;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** With the {@code offline} profile, snapshots come from bundled fixtures and Battle.net is never called. */
@Configuration(proxyBeanMethods = false)
@Profile("offline")
class OfflineConfiguration {

    @Bean
    AuctionSourcePort auctionSource(Clock clock) {
        return new FixtureAuctionSource(clock);
    }
}
