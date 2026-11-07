package com.mia_princz.wowmarket.collector.application.port.out;

import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import java.io.InputStream;
import java.time.Instant;

/** The raw zone: every snapshot kept exactly as received, so the pipeline can be replayed from it. */
public interface RawArchivePort {

    /**
     * Stores a snapshot body, compressed, at a location derived from its source and event time. Storing the same
     * snapshot again replaces the earlier copy.
     *
     * @param gzipped whether {@code body} is already gzip-compressed
     */
    ArchivedSnapshot store(
            String region, AuctionSource source, Instant lastModified, InputStream body, boolean gzipped);

    /**
     * Where a snapshot was stored.
     *
     * @param location path relative to the archive root
     * @param bytes size of the stored, compressed file
     * @param sha256 hex SHA-256 of the stored file, to detect corruption before a replay
     */
    record ArchivedSnapshot(String location, long bytes, String sha256) {}
}
