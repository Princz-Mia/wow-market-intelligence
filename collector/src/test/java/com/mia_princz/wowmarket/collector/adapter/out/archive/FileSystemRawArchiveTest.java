package com.mia_princz.wowmarket.collector.adapter.out.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.HexFormat;
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
