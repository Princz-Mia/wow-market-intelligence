package com.mia_princz.wowmarket.collector.config;

import com.mia_princz.wowmarket.collector.adapter.in.web.RunFreshnessHealthIndicator;
import com.mia_princz.wowmarket.collector.application.port.in.PipelineStatusUseCase;
import com.mia_princz.wowmarket.collector.application.port.out.PipelineStatusPort;
import com.mia_princz.wowmarket.collector.application.service.PipelineStatusService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The operator API's controller is picked up by component scanning; this wires what it and the health check use. */
@Configuration(proxyBeanMethods = false)
class WebConfiguration {

    @Bean
    PipelineStatusUseCase pipelineStatus(CollectorProperties properties, PipelineStatusPort status) {
        return new PipelineStatusService(properties.region(), status);
    }

    /** Only while capturing on a schedule; with capture off, old runs would report the collector down. */
    @Bean
    @ConditionalOnProperty(name = "collector.capture.enabled", havingValue = "true")
    RunFreshnessHealthIndicator runFreshness(
            PipelineStatusUseCase status, Clock clock, CollectorProperties properties) {
        return new RunFreshnessHealthIndicator(
                status, clock, properties.capture().maxRunAge());
    }
}
