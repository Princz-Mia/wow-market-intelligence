package com.mia_princz.wowmarket.collector.config;

import com.mia_princz.wowmarket.collector.adapter.out.postgres.CopyStagingWriter;
import com.mia_princz.wowmarket.collector.adapter.out.postgres.JdbcPipelineRunRepository;
import com.mia_princz.wowmarket.collector.adapter.out.postgres.JdbcPipelineStatus;
import com.mia_princz.wowmarket.collector.adapter.out.postgres.JdbcSnapshotLoadRegistry;
import com.mia_princz.wowmarket.collector.adapter.out.postgres.JdbcSnapshotRegistry;
import com.mia_princz.wowmarket.collector.adapter.out.postgres.PartitionStagingRetention;
import com.mia_princz.wowmarket.collector.application.port.out.PipelineRunRepositoryPort;
import com.mia_princz.wowmarket.collector.application.port.out.PipelineStatusPort;
import com.mia_princz.wowmarket.collector.application.port.out.SnapshotRegistryPort;
import com.mia_princz.wowmarket.collector.application.port.out.StagingRetentionPort;
import com.mia_princz.wowmarket.collector.application.port.out.StagingWriterPort;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class PersistenceConfiguration {

    @Bean
    SnapshotRegistryPort snapshotRegistry(JdbcClient jdbc) {
        return new JdbcSnapshotRegistry(jdbc);
    }

    @Bean
    PipelineRunRepositoryPort pipelineRunRepository(JdbcClient jdbc) {
        return new JdbcPipelineRunRepository(jdbc);
    }

    @Bean
    JdbcSnapshotLoadRegistry snapshotLoadRegistry(JdbcClient jdbc, PlatformTransactionManager transactions) {
        return new JdbcSnapshotLoadRegistry(jdbc, new TransactionTemplate(transactions));
    }

    @Bean
    PipelineStatusPort pipelineStatusQueries(JdbcClient jdbc) {
        return new JdbcPipelineStatus(jdbc);
    }

    @Bean
    StagingRetentionPort stagingRetention(JdbcClient jdbc) {
        return new PartitionStagingRetention(jdbc);
    }

    @Bean
    StagingWriterPort stagingWriter(DataSource dataSource) {
        return new CopyStagingWriter(dataSource);
    }
}
