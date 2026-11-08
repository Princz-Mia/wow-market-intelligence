package com.mia_princz.wowmarket.collector.application.port.out;

import com.mia_princz.wowmarket.collector.domain.market.AuctionListing;
import com.mia_princz.wowmarket.collector.domain.market.AuctionSource;
import com.mia_princz.wowmarket.collector.domain.market.ItemVariantKey;
import java.time.Instant;

/** Writes the listings of a snapshot into staging, all or nothing. */
public interface StagingWriterPort {

    /**
     * Starts staging one snapshot. Listings added to the batch become visible only on {@link StagingBatch#commit()},
     * and then replace whatever was staged for this snapshot before, so loading it twice changes nothing.
     */
    StagingBatch open(long snapshotId, AuctionSource source, Instant lastModified);

    interface StagingBatch extends AutoCloseable {

        /** Stages a listing that passed the dropping checks, so its item id is known. */
        void add(AuctionListing listing, ItemVariantKey variant);

        /** Makes the batch visible and returns how many listings it holds. */
        long commit();

        /** Discards the batch unless it was committed. */
        @Override
        void close();
    }
}
