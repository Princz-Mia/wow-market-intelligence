package com.mia_princz.wowmarket.collector.config;

import com.mia_princz.wowmarket.collector.adapter.out.blizzard.BlizzardAuctionClient;
import com.mia_princz.wowmarket.collector.adapter.out.blizzard.BlizzardTokenProvider;
import java.net.http.HttpClient;
import java.time.Clock;
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

    @Bean
    BlizzardAuctionClient blizzardAuctionClient(
            HttpClient blizzardHttpClient, BlizzardTokenProvider tokens, CollectorProperties properties) {
        return new BlizzardAuctionClient(
                blizzardHttpClient,
                tokens,
                properties.blizzard().apiUrl(),
                properties.region(),
                properties.blizzard().requestTimeout());
    }
}
