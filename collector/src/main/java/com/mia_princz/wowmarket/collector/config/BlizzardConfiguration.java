package com.mia_princz.wowmarket.collector.config;

import com.mia_princz.wowmarket.collector.adapter.out.blizzard.BlizzardAuctionClient;
import com.mia_princz.wowmarket.collector.adapter.out.blizzard.BlizzardTokenProvider;
import com.mia_princz.wowmarket.collector.adapter.out.blizzard.ResilientAuctionSource;
import com.mia_princz.wowmarket.collector.application.port.out.AuctionSourcePort;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class BlizzardConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    HttpClient blizzardHttpClient(CollectorProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.blizzard().connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    BlizzardTokenProvider blizzardTokenProvider(
            HttpClient blizzardHttpClient, JsonMapper json, Clock clock, CollectorProperties properties) {
        CollectorProperties.Blizzard blizzard = properties.blizzard();
        return new BlizzardTokenProvider(
                blizzardHttpClient,
                json,
                clock,
                blizzard.tokenUrl(),
                blizzard.clientId(),
                blizzard.clientSecret(),
                blizzard.requestTimeout());
    }

    /** One limiter for every Blizzard call the collector makes. */
    @Bean
    RateLimiter blizzardRateLimiter(CollectorProperties properties) {
        return RateLimiter.of(
                "blizzard",
                RateLimiterConfig.custom()
                        .limitForPeriod(properties.blizzard().requestsPerSecond())
                        .limitRefreshPeriod(Duration.ofSeconds(1))
                        .timeoutDuration(Duration.ofMinutes(1))
                        .build());
    }

    @Bean
    AuctionSourcePort auctionSource(
            HttpClient blizzardHttpClient,
            BlizzardTokenProvider tokens,
            RateLimiter blizzardRateLimiter,
            CollectorProperties properties) {
        var client = new BlizzardAuctionClient(
                blizzardHttpClient,
                tokens,
                properties.blizzard().apiUrl(),
                properties.region(),
                properties.blizzard().requestTimeout());
        return new ResilientAuctionSource(
                client, blizzardRateLimiter, properties.blizzard().maxAttempts(), Duration.ofSeconds(1));
    }
}
