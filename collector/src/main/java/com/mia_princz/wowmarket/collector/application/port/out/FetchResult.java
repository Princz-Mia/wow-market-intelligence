package com.mia_princz.wowmarket.collector.application.port.out;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Objects;

/** Outcome of a conditional snapshot fetch. */
public sealed interface FetchResult permits FetchResult.NotModified, FetchResult.Modified {

    /** The source still serves the snapshot we already have. */
    record NotModified() implements FetchResult {}

    /**
     * A snapshot newer than the one we asked about.
     *
     * @param lastModified the snapshot's event time, from the {@code Last-Modified} header
     * @param body the response body as it arrives on the wire, still open
     * @param gzipped whether the body is gzip-compressed, so it can be archived without recompressing
     */
    record Modified(Instant lastModified, InputStream body, boolean gzipped) implements FetchResult, AutoCloseable {
        public Modified {
            Objects.requireNonNull(lastModified, "lastModified");
            Objects.requireNonNull(body, "body");
        }

        @Override
        public void close() throws IOException {
            body.close();
        }
    }
}
