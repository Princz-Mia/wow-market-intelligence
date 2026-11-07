package com.mia_princz.wowmarket.collector.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CollectorProperties.class)
class CollectorConfiguration {}
