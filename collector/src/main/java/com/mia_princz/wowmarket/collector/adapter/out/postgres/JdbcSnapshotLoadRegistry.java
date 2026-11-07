package com.mia_princz.wowmarket.collector.adapter.out.postgres;

import com.mia_princz.wowmarket.collector.application.port.out.SnapshotLoadRegistryPort;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.quality.ValidationContext.History;
import com.mia_princz.wowmarket.collector.domain.quality.ValidationResult;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

public class JdbcSnapshotLoadRegistry implements SnapshotLoadRegistryPort {

    static final Duration HISTORY_WINDOW = Duration.ofDays(7);

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    public JdbcSnapshotLoadRegistry(JdbcClient jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    @Override
    public List<PendingSnapshot> pending(String region, int limit) {
        return jdbc.sql("""
                        SELECT id, source, last_modified, archive_location FROM ops.snapshot
                        WHERE status = 'ARCHIVED' AND region = ?
                        ORDER BY last_modified, id
                        LIMIT ?""")
                .params(region, limit)
                .query((rs, n) -> new PendingSnapshot(
                        rs.getLong("id"),
                        AuctionSource.fromKey(rs.getString("source")),
                        rs.getTimestamp("last_modified").toInstant(),
                        rs.getString("archive_location")))
                .list();
    }

    @Override
    public Optional<Instant> lastLoaded(String region, AuctionSource source) {
        return jdbc.sql("""
                        SELECT max(last_modified) FROM ops.snapshot
                        WHERE region = ? AND source = ? AND status = 'LOADED'""")
                .params(region, source.key())
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant);
    }

    /**
     * Quarantined snapshots count too: their volumes are still facts about the source, and leaving them out would keep
     * a lasting change in volume, such as a new patch, quarantined until the whole window had passed.
     */
    @Override
    public History history(String region, AuctionSource source, Instant eventTime) {
        return jdbc.sql("""
                        SELECT count(*) AS snapshots,
                               coalesce(percentile_cont(0.5) WITHIN GROUP (ORDER BY listings_read), 0) AS listings,
                               coalesce(percentile_cont(0.5) WITHIN GROUP (ORDER BY distinct_items), 0) AS items
                        FROM ops.snapshot
                        WHERE region = ? AND source = ? AND listings_read IS NOT NULL
                          AND last_modified >= ? AND last_modified < ?""")
                .params(
                        region,
                        source.key(),
                        Timestamp.from(eventTime.minus(HISTORY_WINDOW)),
                        Timestamp.from(eventTime))
                .query((rs, n) -> new History(rs.getInt("snapshots"), rs.getDouble("listings"), rs.getDouble("items")))
                .single();
    }

    @Override
    public void recordLoad(LoadRecord load) {
        transaction.executeWithoutResult(status -> {
            jdbc.sql("""
                            UPDATE ops.snapshot
                            SET status = ?, listings_read = ?, listings_staged = ?, distinct_items = ?, processed_at = ?
                            WHERE id = ?""")
                    .params(
                            load.loaded() ? "LOADED" : "QUARANTINED",
                            load.listingsRead(),
                            load.listingsStaged(),
                            load.distinctItems(),
                            Timestamp.from(load.processedAt()),
                            load.snapshotId())
                    .update();
            jdbc.sql("DELETE FROM ops.validation_result WHERE snapshot_id = ?")
                    .param(load.snapshotId())
                    .update();
            for (ValidationResult result : load.results()) {
                jdbc.sql("""
                                INSERT INTO ops.validation_result (snapshot_id, rule_id, outcome, detail, checked_at)
                                VALUES (?, ?, ?, ?, ?)""")
                        .params(
                                load.snapshotId(),
                                result.ruleId(),
                                outcome(result),
                                result.detail(),
                                Timestamp.from(load.processedAt()))
                        .update();
            }
        });
    }

    private static String outcome(ValidationResult result) {
        return switch (result) {
            case ValidationResult.Passed ignored -> "PASSED";
            case ValidationResult.Warned ignored -> "WARNED";
            case ValidationResult.Failed ignored -> "FAILED";
        };
    }
}
