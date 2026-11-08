package com.mia_princz.wowmarket.collector.adapter.out.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mia_princz.wowmarket.collector.application.port.out.RawArchivePort;
import com.mia_princz.wowmarket.collector.application.port.out.RawArchivePort.ArchivedSnapshot;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemRawArchiveTest {

    private static final String JSON = "{\"auctions\":[]}";
    private static final Instant EVENT_TIME = Instant.parse("2026-11-07T17:21:54Z");

    @TempDir
    Path root;

    @Test
    void storesACompressedBodyAsReceivedUnderItsEventTime() throws Exception {
        byte[] compressed = gzip(JSON);
        var archive = new FileSystemRawArchive(root);

        ArchivedSnapshot stored = archive.store(
                "eu", new AuctionSource.ConnectedRealm(1403), EVENT_TIME, new ByteArrayInputStream(compressed), true);

        assertThat(stored.location()).isEqualTo("eu/connected-realm-1403/2026/11/07/17/20261107T172154Z.json.gz");
        Path file = root.resolve(stored.location());
        assertThat(Files.readAllBytes(file)).isEqualTo(compressed);
        assertThat(stored.bytes()).isEqualTo(compressed.length);
        assertThat(stored.sha256()).isEqualTo(sha256(compressed));
    }

    @Test
    void compressesABodyThatArrivedUncompressed() throws Exception {
        var archive = new FileSystemRawArchive(root);

        ArchivedSnapshot stored = archive.store(
                "eu",
                new AuctionSource.RegionCommodities(),
                EVENT_TIME,
                new ByteArrayInputStream(JSON.getBytes(StandardCharsets.UTF_8)),
                false);

        byte[] file = Files.readAllBytes(root.resolve(stored.location()));
        assertThat(gunzip(file)).isEqualTo(JSON);
        assertThat(stored.sha256()).isEqualTo(sha256(file));
    }

    @Test
    void replacesAnEarlierCopyOfTheSameSnapshot() throws Exception {
        var archive = new FileSystemRawArchive(root);
        var source = new AuctionSource.RegionCommodities();
        archive.store("eu", source, EVENT_TIME, new ByteArrayInputStream(gzip("{\"old\":1}")), true);

        ArchivedSnapshot stored = archive.store("eu", source, EVENT_TIME, new ByteArrayInputStream(gzip(JSON)), true);

        assertThat(gunzip(Files.readAllBytes(root.resolve(stored.location())))).isEqualTo(JSON);
    }

    @Test
    void leavesNoPartialFileWhenTheStreamBreaks() throws IOException {
        var archive = new FileSystemRawArchive(root);
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("connection reset");
            }
        };

        assertThatThrownBy(() -> archive.store("eu", new AuctionSource.RegionCommodities(), EVENT_TIME, broken, true))
                .hasMessageContaining("could not archive eu/commodities/2026/11/07/17/");
        try (Stream<Path> files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void readsAStoredSnapshotBackDecompressed() throws IOException {
        var archive = new FileSystemRawArchive(root);
        ArchivedSnapshot stored = archive.store(
                "eu", new AuctionSource.RegionCommodities(), EVENT_TIME, new ByteArrayInputStream(gzip(JSON)), true);

        try (InputStream json = archive.open(stored.location())) {
            assertThat(new String(json.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(JSON);
        }
    }

    @Test
    void deletesWholeDaysBeforeTheFirstKeptDayAndTidiesEmptyFolders() throws IOException {
        var archive = new FileSystemRawArchive(root);
        var commodities = new AuctionSource.RegionCommodities();
        var draenor = new AuctionSource.ConnectedRealm(1403);
        for (String time : List.of("2026-10-31T23:21:54Z", "2026-11-01T00:21:54Z", "2026-11-02T10:21:54Z")) {
            archive.store("eu", commodities, Instant.parse(time), new ByteArrayInputStream(gzip(JSON)), true);
            archive.store("eu", draenor, Instant.parse(time), new ByteArrayInputStream(gzip(JSON)), true);
        }
        archive.store(
                "us", commodities, Instant.parse("2026-10-31T23:21:54Z"), new ByteArrayInputStream(gzip(JSON)), true);
        Files.createDirectories(root.resolve("eu/commodities/notes"));

        int deleted = archive.deleteDaysBefore("eu", LocalDate.parse("2026-11-02"));

        assertThat(deleted).isEqualTo(4);
        try (Stream<Path> files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile)
                            .map(path -> root.relativize(path).toString().replace('\\', '/')))
                    .containsExactlyInAnyOrder(
                            "eu/commodities/2026/11/02/10/20261102T102154Z.json.gz",
                            "eu/connected-realm-1403/2026/11/02/10/20261102T102154Z.json.gz",
                            "us/commodities/2026/10/31/23/20261031T232154Z.json.gz");
        }
        assertThat(root.resolve("eu/commodities/2026/10")).doesNotExist();
        assertThat(root.resolve("eu/commodities/notes")).exists();
    }

    @Test
    void deletesNothingInARegionWithoutSnapshots() {
        assertThat(new FileSystemRawArchive(root).deleteDaysBefore("kr", LocalDate.parse("2026-11-02")))
                .isZero();
    }

    @Test
    void reportsAMissingFileAsSuch() {
        var archive = new FileSystemRawArchive(root);

        assertThatThrownBy(() -> archive.open("eu/commodities/2026/11/07/17/20261107T172154Z.json.gz"))
                .isInstanceOf(RawArchivePort.MissingSnapshotFileException.class)
                .hasMessage("archive file missing: eu/commodities/2026/11/07/17/20261107T172154Z.json.gz");
    }

    @Test
    void refusesToOpenAFileOutsideTheArchive() {
        var archive = new FileSystemRawArchive(root.resolve("archive"));

        assertThatThrownBy(() -> archive.open("../secrets.json.gz")).isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] gzip(String text) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new GZIPOutputStream(bytes)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    private static String gunzip(byte[] compressed) throws IOException {
        try (var in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
