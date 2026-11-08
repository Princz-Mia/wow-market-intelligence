package com.mia_princz.wowmarket.collector.application.port.out;

import com.mia_princz.wowmarket.collector.domain.market.AuctionListing;
import java.io.InputStream;
import java.util.function.Consumer;

/** Reads the listings of a snapshot body one at a time, without holding the whole payload in memory. */
public interface AuctionPayloadParserPort {

    /**
     * Hands every listing of {@code json} to {@code listings}, in payload order. A listing with missing or mistyped
     * fields is still handed over, with those fields null, so that the ingestion checks can count it.
     *
     * @return the number of listings read
     * @throws MalformedPayloadException if the body is not JSON or lacks the expected structure
     */
    long parse(InputStream json, Consumer<AuctionListing> listings);

    /** The body cannot be read as an auction snapshot at all. */
    final class MalformedPayloadException extends RuntimeException {
        public MalformedPayloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
