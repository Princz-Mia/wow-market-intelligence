package com.mia_princz.wowmarket.collector.adapter.out.postgres;

import com.mia_princz.wowmarket.collector.application.port.in.PipelineStatusUseCase.RunView;
import com.mia_princz.wowmarket.collector.application.port.in.PipelineStatusUseCase.SnapshotView;
import com.mia_princz.wowmarket.collector.application.port.out.PipelineStatusPort;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcPipelineStatus implements PipelineStatusPort {

    private final JdbcClient jdbc;

    public JdbcPipelineStatus(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RunView> latestRun() {
        return jdbc.sql("SELECT * FROM ops.pipeline_run ORDER BY id DESC LIMIT 1")
                .query((rs, n) -> new RunView(
                        rs.getLong("id"),
                        rs.getString("run_trigger"),
                        rs.getString("status"),
                        instant(rs, "started_at"),
                        instant(rs, "finished_at"),
                        rs.getInt("sources_total"),
                        rs.getInt("snapshots_new"),
                        rs.getInt("sources_unchanged"),
                        rs.getInt("sources_failed"),
                        rs.getString("error_summary")))
                .optional();
    }

    @Override
    public List<SnapshotView> recentSnapshots(String region, Optional<String> sourceKey, int limit) {
        return jdbc.sql("""
                        SELECT s.id, s.source, s.last_modified, s.fetched_at, s.status, s.listings_read,
                               s.listings_staged, s.processed_at,
                               ARRAY(SELECT v.rule_id || ' ' || v.outcome || ': ' || v.detail
                                     FROM ops.validation_result v
                                     WHERE v.snapshot_id = s.id AND v.outcome <> 'PASSED'
                                     ORDER BY v.rule_id) AS checks_not_passed
                        FROM ops.snapshot s
                        WHERE s.region = :region AND (CAST(:source AS text) IS NULL OR s.source = :source)
                        ORDER BY s.last_modified DESC, s.source
                        LIMIT :limit""")
                .param("region", region)
                .param("source", sourceKey.orElse(null))
                .param("limit", limit)
                .query((rs, n) -> new SnapshotView(
                        rs.getLong("id"),
                        rs.getString("source"),
                        instant(rs, "last_modified"),
                        instant(rs, "fetched_at"),
                        rs.getString("status"),
                        rs.getObject("listings_read", Integer.class),
                        rs.getObject("listings_staged", Integer.class),
                        instant(rs, "processed_at"),
                        strings(rs.getArray("checks_not_passed"))))
                .list();
    }

    @Override
    public Optional<Instant> lastCompletedRun() {
        return jdbc.sql("SELECT max(finished_at) FROM ops.pipeline_run WHERE status IN ('SUCCEEDED', 'PARTIAL')")
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static List<String> strings(Array array) throws SQLException {
        return Arrays.stream((Object[]) array.getArray()).map(String::valueOf).toList();
    }
}
