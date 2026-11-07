package com.mia_princz.wowmarket.collector.adapter.out.archive;

import com.mia_princz.wowmarket.collector.application.port.out.RawArchivePort;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Keeps snapshots as gzipped JSON files under {@code {root}/{region}/{source}/yyyy/MM/dd/HH/{event time}.json.gz},
 * partitioned by event time in UTC. A file is written next to its final name and moved into place only when complete,
 * so a crash never leaves a truncated snapshot behind.
 */
public class FileSystemRawArchive implements RawArchivePort {

    private static final DateTimeFormatter FOLDER = DateTimeFormatter.ofPattern("yyyy/MM/dd/HH");
    private static final DateTimeFormatter FILE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final Path root;

    public FileSystemRawArchive(Path root) {
        this.root = root;
    }

    @Override
    public ArchivedSnapshot store(
            String region, AuctionSource source, Instant lastModified, InputStream body, boolean gzipped) {
        String location = location(region, source, lastModified);
        Path target = root.resolve(location);
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        try {
            Files.createDirectories(target.getParent());
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            try (OutputStream file = Files.newOutputStream(partial);
                    DigestOutputStream digested = new DigestOutputStream(file, sha256)) {
                if (gzipped) {
                    body.transferTo(digested);
                } else {
                    try (GZIPOutputStream compressed = new GZIPOutputStream(digested)) {
                        body.transferTo(compressed);
                    }
                }
            }
            long bytes = Files.size(partial);
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return new ArchivedSnapshot(location, bytes, HexFormat.of().formatHex(sha256.digest()));
        } catch (IOException e) {
            deleteQuietly(partial);
            throw new UncheckedIOException("could not archive " + location, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JDK provides SHA-256", e);
        }
    }

    @Override
    public InputStream open(String location) {
        Path file = root.resolve(location).normalize();
        if (!file.startsWith(root.normalize())) {
            throw new IllegalArgumentException("location outside the archive: " + location);
        }
        try {
            return new GZIPInputStream(Files.newInputStream(file), 64 * 1024);
        } catch (IOException e) {
            throw new UncheckedIOException("could not open " + location, e);
        }
    }

    static String location(String region, AuctionSource source, Instant lastModified) {
        var utc = lastModified.atOffset(ZoneOffset.UTC);
        return region + "/" + source.key() + "/" + FOLDER.format(utc) + "/" + FILE.format(utc) + ".json.gz";
    }

    private static void deleteQuietly(Path partial) {
        try {
            Files.deleteIfExists(partial);
        } catch (IOException ignored) {
            // Nothing else to do; the next store of this snapshot overwrites it.
        }
    }
}
