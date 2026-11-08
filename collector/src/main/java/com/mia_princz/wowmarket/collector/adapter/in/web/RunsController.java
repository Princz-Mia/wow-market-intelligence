package com.mia_princz.wowmarket.collector.adapter.in.web;

import com.mia_princz.wowmarket.collector.application.port.in.PipelineStatusUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.PipelineStatusUseCase.RunView;
import com.mia_princz.wowmarket.collector.application.port.in.PipelineStatusUseCase.SnapshotView;
import com.mia_princz.wowmarket.collector.application.port.in.RunPipelineUseCase;
import com.mia_princz.wowmarket.collector.application.port.in.RunPipelineUseCase.PipelineAlreadyRunningException;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.pipeline.PipelineSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.RunTrigger;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A small operator API: start a run by hand and see what recent runs and snapshots did. The collector publishes no
 * port, so it is reachable only from the host, e.g. {@code docker compose exec collector curl ...}.
 */
@RestController
@RequestMapping("/api")
public class RunsController {

    private final RunPipelineUseCase pipeline;
    private final PipelineStatusUseCase status;

    public RunsController(RunPipelineUseCase pipeline, PipelineStatusUseCase status) {
        this.pipeline = pipeline;
        this.status = status;
    }

    /** Runs capture, load and retention now and answers when they are done, usually within seconds. */
    @PostMapping("/runs")
    public PipelineSummary run() {
        return pipeline.run(RunTrigger.MANUAL);
    }

    @GetMapping("/runs/latest")
    public ResponseEntity<RunView> latestRun() {
        return ResponseEntity.of(status.latestRun());
    }

    @GetMapping("/snapshots")
    public List<SnapshotView> snapshots(
            @RequestParam Optional<String> source, @RequestParam(defaultValue = "20") int limit) {
        return status.recentSnapshots(source.map(AuctionSource::fromKey), limit);
    }

    @ExceptionHandler(PipelineAlreadyRunningException.class)
    ProblemDetail alreadyRunning(PipelineAlreadyRunningException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
