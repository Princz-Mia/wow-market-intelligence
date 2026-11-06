package com.mia_princz.wowmarket.collector;

import java.nio.file.Path;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    // Digest matches compose.yaml (postgres:18.6).
    static final String POSTGRES_IMAGE =
            "postgres@sha256:3a4264520e121176619f54ff867d12774bc81e95e7b84db58d63bccdb6144b81";

    static final String COLLECTOR_PASSWORD = "test-collector";
    static final String TRANSFORMER_PASSWORD = "test-transformer";
    static final String BI_READER_PASSWORD = "test-bi-reader";

    /** Starts from the same init script as Compose, so tests exercise the real roles and grants. */
    @Bean
    PostgreSQLContainer postgresContainer() {
        Path initScript = Path.of("../infra/postgres/init/10-warehouse.sh")
                .toAbsolutePath()
                .normalize();
        return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("postgres")
                .withEnv("COLLECTOR_DB_PASSWORD", COLLECTOR_PASSWORD)
                .withEnv("TRANSFORMER_DB_PASSWORD", TRANSFORMER_PASSWORD)
                .withEnv("BI_READER_DB_PASSWORD", BI_READER_PASSWORD)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(initScript), "/docker-entrypoint-initdb.d/10-warehouse.sh");
    }

    @Bean
    DynamicPropertyRegistrar databaseProperties(PostgreSQLContainer postgres) {
        return registry -> {
            registry.add("spring.datasource.url", () -> warehouseUrl(postgres));
            registry.add("spring.datasource.password", () -> COLLECTOR_PASSWORD);
        };
    }

    static String warehouseUrl(PostgreSQLContainer postgres) {
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/wow_market";
    }
}
