package com.mia_princz.wowmarket.collector;

import static org.assertj.core.api.Assertions.assertThat;

import com.mia_princz.wowmarket.collector.application.port.in.RunPipelineUseCase;
import com.mia_princz.wowmarket.collector.domain.pipeline.PipelineSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.RunTrigger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The whole pipeline on bundled fixtures, without calling Battle.net. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("offline")
class OfflineProfileIT {

    @TempDir
    static Path archiveRoot;

    @DynamicPropertySource
    static void archive(DynamicPropertyRegistry registry) {
        registry.add("collector.archive.root", archiveRoot::toString);
    }

    @Autowired
    RunPipelineUseCase pipeline;

    @Autowired
    JdbcClient jdbc;

    @Test
    void capturesAndLoadsEveryFixtureOnceThenSeesNothingNew() {
        PipelineSummary first = pipeline.run(RunTrigger.MANUAL);
        PipelineSummary second = pipeline.run(RunTrigger.MANUAL);
        PipelineSummary third = pipeline.run(RunTrigger.MANUAL);

        assertThat(first.stepErrors()).isEmpty();
        assertThat(first.capture().snapshotsNew()).isEqualTo(3);
        assertThat(first.load().loaded()).isEqualTo(3);
        assertThat(second.capture().snapshotsNew()).isEqualTo(3);
        assertThat(second.load().loaded()).isEqualTo(3);
        assertThat(third.capture().sourcesUnchanged()).isEqualTo(3);
        assertThat(third.load().loaded()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM raw.auction_listing")
                        .query(Long.class)
                        .single())
                .isEqualTo(first.load().listingsStaged() + second.load().listingsStaged())
                .isEqualTo(24);
        assertThat(jdbc.sql("SELECT count(*) FROM ops.snapshot WHERE status <> 'LOADED'")
                        .query(Long.class)
                        .single())
                .isZero();
    }
}
