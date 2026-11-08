package com.mia_princz.wowmarket.collector.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mia_princz.wowmarket.collector.application.port.in.PipelineStatusUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.ReplayArchiveUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.RunPipelineUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.RunPipelineUseCase.PipelineAlreadyRunningException;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.pipeline.CaptureSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.LoadSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.PipelineSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.ReplaySummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.RetentionSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.RunTrigger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RunsControllerTest {

    private static final Instant EVENT_TIME = Instant.parse("2026-11-08T08:21:53Z");

    private final List<String> asked = new ArrayList<>();
    private RunPipelineUseCase pipeline = trigger -> {
        asked.add("run " + trigger);
        return new PipelineSummary(
                new CaptureSummary(9, 9, 0, List.of()),
                new LoadSummary(9, 0, 0, 725_000),
                new RetentionSummary(List.of(), 0),
                List.of());
    };
    private Optional<PipelineStatusUseCase.RunView> latest = Optional.empty();

    private final PipelineStatusUseCase status = new PipelineStatusUseCase() {
        @Override
        public Optional<RunView> latestRun() {
            return latest;
        }

        @Override
        public List<SnapshotView> recentSnapshots(Optional<AuctionSource> source, int limit) {
            asked.add("snapshots " + source.map(AuctionSource::key).orElse("all") + " " + limit);
            return List.of(new SnapshotView(
                    7,
                    "commodities",
                    EVENT_TIME,
                    EVENT_TIME.plusSeconds(1100),
                    "LOADED",
                    209_161,
                    209_161,
                    EVENT_TIME.plusSeconds(1102),
                    List.of("DQ-I09 WARNED: 1 of 209161 listings flagged (0.000%)")));
        }

        @Override
        public Optional<Instant> lastCompletedRun() {
            return Optional.empty();
        }
    };

    private final ReplayArchiveUseCase replay = from -> {
        if (from.isBefore(Instant.parse("2026-11-05T00:00:00Z"))) {
            throw new IllegalArgumentException("staging keeps days from 2026-11-05");
        }
        asked.add("replay " + from);
        return new ReplaySummary(18, new LoadSummary(18, 0, 0, 1_300_000));
    };

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new RunsController(pipeline, status, replay))
                .build();
    }

    @Test
    void startsAManualRunAndAnswersWithItsSummary() throws Exception {
        mvc().perform(post("/api/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capture.snapshotsNew").value(9))
                .andExpect(jsonPath("$.load.listingsStaged").value(725_000));

        assertThat(asked).containsExactly("run " + RunTrigger.MANUAL);
    }

    @Test
    void answersConflictWhileARunIsGoing() throws Exception {
        pipeline = trigger -> {
            throw new PipelineAlreadyRunningException();
        };

        mvc().perform(post("/api/runs")).andExpect(status().isConflict());
    }

    @Test
    void showsTheLatestRunOrNotFound() throws Exception {
        mvc().perform(get("/api/runs/latest")).andExpect(status().isNotFound());

        latest = Optional.of(new PipelineStatusUseCase.RunView(
                42, "SCHEDULE", "SUCCEEDED", EVENT_TIME, EVENT_TIME.plusSeconds(3), 9, 0, 9, 0, null));

        mvc().perform(get("/api/runs/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.startedAt").value("2026-11-08T08:21:53Z"));
    }

    @Test
    void listsRecentSnapshotsOfOneSourceOrAll() throws Exception {
        mvc().perform(get("/api/snapshots?source=connected-realm-1403&limit=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("LOADED"))
                .andExpect(jsonPath("$[0].checksNotPassed[0]").value(org.hamcrest.Matchers.startsWith("DQ-I09")));
        mvc().perform(get("/api/snapshots")).andExpect(status().isOk());

        assertThat(asked).containsExactly("snapshots connected-realm-1403 5", "snapshots all 20");
    }

    @Test
    void replaysFromTheGivenInstant() throws Exception {
        mvc().perform(post("/api/replay?from=2026-11-08T07:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requeued").value(18))
                .andExpect(jsonPath("$.load.loaded").value(18));

        assertThat(asked).containsExactly("replay 2026-11-08T07:00:00Z");
    }

    @Test
    void refusesAReplayBeyondStagingOrWithoutAValidInstant() throws Exception {
        mvc().perform(post("/api/replay?from=2026-10-01T00:00:00Z")).andExpect(status().isBadRequest());
        mvc().perform(post("/api/replay?from=yesterday")).andExpect(status().isBadRequest());
        mvc().perform(post("/api/replay")).andExpect(status().isBadRequest());
    }

    @Test
    void refusesAnUnknownSource() throws Exception {
        mvc().perform(get("/api/snapshots?source=realm-1403")).andExpect(status().isBadRequest());
    }
}
