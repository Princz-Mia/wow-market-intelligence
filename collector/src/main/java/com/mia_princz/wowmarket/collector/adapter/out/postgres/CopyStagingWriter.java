package com.mia_princz.wowmarket.collector.adapter.out.postgres;

import com.mia_princz.wowmarket.collector.application.port.out.StagingWriterPort;
import com.mia_princz.wowmarket.collector.domain.market.AuctionListing;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.market.ItemVariantKey;
import com.mia_princz.wowmarket.collector.domain.market.ListedItem;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.springframework.jdbc.UncategorizedSQLException;

/**
 * Stages listings with PostgreSQL {@code COPY}, which loads a 200k-listing snapshot in about a second where row
 * inserts would take minutes. Each batch runs in one transaction: it creates the day's partition if needed, deletes
 * any earlier load of the snapshot, and copies the new rows, so readers see either the old load or the new one.
 */
public class CopyStagingWriter implements StagingWriterPort {

    private static final String COPY = """
            COPY raw.auction_listing (snapshot_id, last_modified, connected_realm_id, auction_id, item_id,
                                      item_variant_key, quantity, unit_price, buyout, bid, time_left, item_context,
                                      bonus_lists, pet_species_id, pet_level, pet_quality_id, pet_breed_id)
            FROM STDIN""";
    private static final DateTimeFormatter PARTITION_SUFFIX = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int FLUSH_AT_CHARS = 64 * 1024;

    private final DataSource dataSource;

    public CopyStagingWriter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public StagingBatch open(long snapshotId, AuctionSource source, Instant lastModified) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            createPartition(connection, lastModified);
            deleteEarlierLoad(connection, snapshotId, lastModified);
            CopyIn copy = connection.unwrap(PGConnection.class).getCopyAPI().copyIn(COPY);
            return new CopyBatch(connection, copy, rowPrefix(snapshotId, source, lastModified));
        } catch (SQLException e) {
            discard(connection);
            throw failure("open a staging batch for snapshot " + snapshotId, e);
        }
    }

    static String partitionName(Instant lastModified) {
        return "auction_listing_"
                + PARTITION_SUFFIX.format(lastModified.atOffset(ZoneOffset.UTC).toLocalDate());
    }

    private static void createPartition(Connection connection, Instant lastModified) throws SQLException {
        LocalDate day = lastModified.atOffset(ZoneOffset.UTC).toLocalDate();
        // Bounds carry an explicit UTC offset; a bare timestamp would be read in the session's time zone.
        String sql = "CREATE TABLE IF NOT EXISTS raw." + partitionName(lastModified)
                + " PARTITION OF raw.auction_listing FOR VALUES FROM ('" + day + " 00:00:00+00') TO ('"
                + day.plusDays(1) + " 00:00:00+00')";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void deleteEarlierLoad(Connection connection, long snapshotId, Instant lastModified)
            throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM raw.auction_listing WHERE last_modified = ? AND snapshot_id = ?")) {
            delete.setTimestamp(1, Timestamp.from(lastModified));
            delete.setLong(2, snapshotId);
            delete.executeUpdate();
        }
    }

    private static String rowPrefix(long snapshotId, AuctionSource source, Instant lastModified) {
        String realm = switch (source) {
            case AuctionSource.RegionCommodities ignored -> "\\N";
            case AuctionSource.ConnectedRealm realmSource -> Integer.toString(realmSource.connectedRealmId());
        };
        return snapshotId + "\t" + lastModified + "\t" + realm + "\t";
    }

    private static void discard(Connection connection) {
        if (connection == null) {
            return;
        }
        try (connection) {
            connection.rollback();
        } catch (SQLException ignored) {
            // The connection is being thrown away; the original failure is what matters.
        }
    }

    private static UncategorizedSQLException failure(String task, SQLException e) {
        return new UncategorizedSQLException(task, COPY, e);
    }

    private static final class CopyBatch implements StagingBatch {

        private final Connection connection;
        private final CopyIn copy;
        private final String prefix;
        private final StringBuilder buffer = new StringBuilder(FLUSH_AT_CHARS + 1024);
        private boolean committed;

        CopyBatch(Connection connection, CopyIn copy, String prefix) {
            this.connection = connection;
            this.copy = copy;
            this.prefix = prefix;
        }

        @Override
        public void add(AuctionListing listing, ItemVariantKey variant) {
            ListedItem item = listing.item();
            buffer.append(prefix);
            field(listing.id()).append('\t');
            field(item.id()).append('\t');
            buffer.append(variant.value()).append('\t');
            field(listing.quantity()).append('\t');
            field(listing.unitPrice()).append('\t');
            field(listing.buyout()).append('\t');
            field(listing.bid()).append('\t');
            text(listing.timeLeft()).append('\t');
            field(item.context()).append('\t');
            array(item.bonusLists()).append('\t');
            field(item.petSpeciesId()).append('\t');
            field(item.petLevel()).append('\t');
            field(item.petQualityId()).append('\t');
            field(item.petBreedId()).append('\n');
            if (buffer.length() >= FLUSH_AT_CHARS) {
                flush();
            }
        }

        @Override
        public long commit() {
            try {
                flush();
                long rows = copy.endCopy();
                connection.commit();
                committed = true;
                return rows;
            } catch (SQLException e) {
                throw failure("commit a staging batch", e);
            }
        }

        @Override
        public void close() {
            if (!committed) {
                try {
                    if (copy.isActive()) {
                        copy.cancelCopy();
                    }
                } catch (SQLException ignored) {
                    // Rolling back below discards the copy anyway.
                }
                discard(connection);
                return;
            }
            try {
                connection.close();
            } catch (SQLException e) {
                throw failure("release the staging connection", e);
            }
        }

        private void flush() {
            if (buffer.isEmpty()) {
                return;
            }
            byte[] bytes = buffer.toString().getBytes(StandardCharsets.UTF_8);
            buffer.setLength(0);
            try {
                copy.writeToCopy(bytes, 0, bytes.length);
            } catch (SQLException e) {
                throw failure("copy listings", e);
            }
        }

        private StringBuilder field(Number value) {
            return value == null ? buffer.append("\\N") : buffer.append(value);
        }

        private StringBuilder array(List<Integer> values) {
            if (values.isEmpty()) {
                return buffer.append("\\N");
            }
            buffer.append('{');
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    buffer.append(',');
                }
                buffer.append(values.get(i));
            }
            return buffer.append('}');
        }

        /** COPY text format: backslash, tab and line breaks must be escaped. */
        private StringBuilder text(String value) {
            if (value == null) {
                return buffer.append("\\N");
            }
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '\\' -> buffer.append("\\\\");
                    case '\t' -> buffer.append("\\t");
                    case '\n' -> buffer.append("\\n");
                    case '\r' -> buffer.append("\\r");
                    default -> buffer.append(c);
                }
            }
            return buffer;
        }
    }
}
