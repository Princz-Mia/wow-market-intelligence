package com.mia_princz.wowmarket.collector.config;

import com.mia_princz.wowmarket.collector.adapter.in.scheduling.CaptureScheduler;
import com.mia_princz.wowmarket.collector.application.port.in.CaptureSnapshotsUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.LoadSnapshotsUseCase;
import com.mia_princz.wowmarket.collector.application.port.out.AuctionPayloadParserPort;
import com.mia_princz.wowmarket.collector.application.port.out.AuctionSourcePort;
import com.mia_princz.wowmarket.collector.application.port.out.PipelineRunRepositoryPort;
import com.mia_princz.wowmarket.collector.application.port.out.RawArchivePort;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotLoadRegistryPort;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotRegistryPort;
import com.mia_princz.wowmarket.collector.application.port.out.StagingWriterPort;
import com.mia_princz.wowmarket.collector.application.service.CaptureSnapshotsService;
import com.mia_princz.wowmarket.collector.application.service.LoadSnapshotsService;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class CaptureConfiguration {

    /** The region-wide commodity market first, then each configured connected realm. */
    static List<AuctionSource> sources(CollectorProperties properties) {
        List<AuctionSource> sources = new ArrayList<>();
        sources.add(new AuctionSource.RegionCommodities());
        properties.connectedRealms().forEach(id -> sources.add(new AuctionSource.ConnectedRealm(id)));
        return sources;
    }

    @Bean
    CaptureSnapshotsUseCase captureSnapshots(
            CollectorProperties properties,
            AuctionSourcePort auctionSource,
            RawArchivePort archive,
            SnapshotRegistryPort registry,
            PipelineRunRepositoryPort runs,
            Clock clock) {
        return new CaptureSnapshotsService(
                properties.region(),
                sources(properties),
                properties.capture().concurrency(),
                auctionSource,
                archive,
                registry,
                runs,
                clock);
    }

    @Bean
    LoadSnapshotsUseCase loadSnapshots(
            CollectorProperties properties,
            RawArchivePort archive,
            AuctionPayloadParserPort parser,
            StagingWriterPort staging,
            SnapshotLoadRegistryPort registry,
            Clock clock) {
        return new LoadSnapshotsService(
                properties.region(), properties.load().batchSize(), archive, parser, staging, registry, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "collector.capture.enabled", havingValue = "true")
    CaptureScheduler captureScheduler(CaptureSnapshotsUseCase capture, LoadSnapshotsUseCase load) {
        return new CaptureScheduler(capture, load);
    }
}
