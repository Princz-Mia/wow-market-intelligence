package com.mia_princz.wowmarket.collector.adapter.out.postgres;

import com.mia_princz.wowmarket.collector.application.port.out.SnapshotRegistryPort;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcSnapshotRegistry implements SnapshotRegistryPort {

    private final JdbcClient jdbc;

    public JdbcSnapshotRegistry(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Instant> latestEventTime(String region, AuctionSource source) {
        return jdbc.sql("SELECT max(last_modified) FROM ops.snapshot WHERE region = ? AND source = ?")
                .params(region, source.key())
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant);
    }

    @Override
    public boolean register(NewSnapshot snapshot) {
        int inserted = jdbc.sql("""
                        INSERT INTO ops.snapshot (region, source, last_modified, fetched_at, pipeline_run_id,
                                                  archive_location, archive_bytes, archive_sha256, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ARCHIVED')
                        ON CONFLICT (region, source, last_modified) DO NOTHING
                        """)
                .params(
                        snapshot.region(),
                        snapshot.source().key(),
                        Timestamp.from(snapshot.lastModified()),
                        Timestamp.from(snapshot.fetchedAt()),
                        snapshot.pipelineRunId(),
                        snapshot.archived().location(),
                        snapshot.archived().bytes(),
                        snapshot.archived().sha256())
                .update();
        return inserted == 1;
    }
}
