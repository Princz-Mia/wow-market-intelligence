package com.mia_princz.wowmarket.collector.config;

import com.mia_princz.wowmarket.collector.adapter.out.archive.FileSystemRawArchive;
import com.mia_princz.wowmarket.collector.application.port.out.RawArchivePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ArchiveConfiguration {

    @Bean
    RawArchivePort rawArchive(CollectorProperties properties) {
        return new FileSystemRawArchive(properties.archive().root());
    }
}
