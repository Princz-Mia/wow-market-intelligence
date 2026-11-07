package com.mia_princz.wowmarket.collector.adapter.out.postgres;

import com.mia_princz.wowmarket.collector.application.port.out.PipelineRunRepositoryPort;
import com.mia_princz.wowmarket.collector.domain.pipeline.CaptureSummary;
import com.mia_princz.wowmarket.collector.domain.pipeline.RunTrigger;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcPipelineRunRepository implements PipelineRunRepositoryPort {

    /** Keeps one run record readable even when every source fails with a long message. */
    static final int MAX_ERROR_SUMMARY = 4000;

    private final JdbcClient jdbc;

    public JdbcPipelineRunRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long start(RunTrigger trigger, Instant startedAt, int sourcesTotal) {
        return jdbc.sql("""
                        INSERT INTO ops.pipeline_run (run_trigger, status, started_at, sources_total)
                        VALUES (?, 'RUNNING', ?, ?)
                        RETURNING id
                        """)
                .params(trigger.name(), Timestamp.from(startedAt), sourcesTotal)
                .query(Long.class)
                .single();
    }

    @Override
    public void finish(long runId, Instant finishedAt, CaptureSummary summary) {
        String errors = summary.errors().isEmpty() ? null : String.join("\n", summary.errors());
        if (errors != null && errors.length() > MAX_ERROR_SUMMARY) {
            errors = errors.substring(0, MAX_ERROR_SUMMARY);
        }
        jdbc.sql("""
                        UPDATE ops.pipeline_run
                        SET status = ?, finished_at = ?, snapshots_new = ?, sources_unchanged = ?,
                            sources_failed = ?, error_summary = ?
                        WHERE id = ? AND status = 'RUNNING'
                        """)
                .params(
                        summary.status().name(),
                        Timestamp.from(finishedAt),
                        summary.snapshotsNew(),
                        summary.sourcesUnchanged(),
                        summary.sourcesFailed(),
                        errors,
                        runId)
                .update();
    }
}
