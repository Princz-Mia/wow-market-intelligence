package com.mia_princz.wowmarket.collector.config;

import com.mia_princz.wowmarket.collector.adapter.in.scheduling.CaptureScheduler;
import com.mia_princz.wowmarket.collector.application.port.in.ApplyRetentionUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.CaptureSnapshotsUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.LoadSnapshotsUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.RunPipelineUseCase;
import com.mia_princz.wowmarket.collector.application.port.out.AuctionPayloadParserPort;
import com.mia_princz.wowmarket.collector.application.port.out.AuctionSourcePort;
import com.mia_princz.wowmarket.collector.application.port.out.PipelineRunRepositoryPort;
import com.mia_princz.wowmarket.collector.application.port.out.RawArchivePort;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotLoadRegistryPort;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotRegistryPort;
import com.mia_princz.wowmarket.collector.application.port.out.StagingRetentionPort;
import com.mia_princz.wowmarket.collector.application.port.out.StagingWriterPort;
import com.mia_princz.wowmarket.collector.application.service.ApplyRetentionService;
import com.mia_princz.wowmarket.collector.application.service.CaptureSnapshotsService;
import com.mia_princz.wowmarket.collector.application.service.LoadSnapshotsService;
import com.mia_princz.wowmarket.collector.application.service.PipelineLock;
import com.mia_princz.wowmarket.collector.application.service.RunPipelineService;
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
    ApplyRetentionUseCase applyRetention(
            CollectorProperties properties, StagingRetentionPort staging, RawArchivePort archive, Clock clock) {
        return new ApplyRetentionService(
                properties.region(),
                properties.retention().stagedDays(),
                properties.retention().archiveDays(),
                staging,
                archive,
                clock);
    }

    @Bean
    PipelineLock pipelineLock() {
        return new PipelineLock();
    }

    @Bean
    RunPipelineUseCase runPipeline(
            CaptureSnapshotsUseCase capture,
            LoadSnapshotsUseCase load,
            ApplyRetentionUseCase retention,
            PipelineLock lock) {
        return new RunPipelineService(capture, load, retention, lock);
    }

    @Bean
    @ConditionalOnProperty(name = "collector.capture.enabled", havingValue = "true")
    CaptureScheduler captureScheduler(RunPipelineUseCase pipeline) {
        return new CaptureScheduler(pipeline);
    }
}
