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
     * Opens a stored snapshot for reading, already decompressed. The caller closes the stream.
     *
     * @param location as returned by {@link #store}
     * @throws MissingSnapshotFileException if nothing is stored at {@code location}
     */
    InputStream open(String location);

    /** The registry knows a snapshot whose file is not in the archive; retrying will not bring it back. */
    final class MissingSnapshotFileException extends RuntimeException {
        public MissingSnapshotFileException(String location, Throwable cause) {
            super("archive file missing: " + location, cause);
        }
    }

    /**
     * Where a snapshot was stored.
     *
     * @param location path relative to the archive root
     * @param bytes size of the stored, compressed file
     * @param sha256 hex SHA-256 of the stored file, to detect corruption before a replay
     */
    record ArchivedSnapshot(String location, long bytes, String sha256) {}
}
