package com.mia_princz.wowmarket.collector.adapter.out.postgres;

import com.mia_princz.wowmarket.collector.application.port.out.StagingRetentionPort;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Drops expired days of {@code raw.auction_listing} as whole partitions: instant, and it returns the space to the
 * operating system, where deleting rows would leave a bloated table behind for vacuum.
 */
public class PartitionStagingRetention implements StagingRetentionPort {

    private static final Pattern DAILY_PARTITION = Pattern.compile("auction_listing_(\\d{8})");
    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JdbcClient jdbc;

    public PartitionStagingRetention(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LocalDate> dropDaysBefore(LocalDate firstKeptDay) {
        List<String> partitions = jdbc.sql("""
                        SELECT child.relname
                        FROM pg_inherits
                        JOIN pg_class child ON child.oid = pg_inherits.inhrelid
                        JOIN pg_class parent ON parent.oid = pg_inherits.inhparent
                        JOIN pg_namespace ns ON ns.oid = parent.relnamespace
                        WHERE ns.nspname = 'raw' AND parent.relname = 'auction_listing'
                        ORDER BY child.relname""").query(String.class).list();
        List<LocalDate> dropped = new ArrayList<>();
        for (String partition : partitions) {
            Optional<LocalDate> day = dayOf(partition);
            if (day.isPresent() && day.get().isBefore(firstKeptDay)) {
                // The name matched the daily pattern, so it is safe to put into the statement.
                jdbc.sql("DROP TABLE raw." + partition).update();
                dropped.add(day.get());
            }
        }
        return dropped;
    }

    private static Optional<LocalDate> dayOf(String partition) {
        Matcher matcher = DAILY_PARTITION.matcher(partition);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(matcher.group(1), SUFFIX));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
