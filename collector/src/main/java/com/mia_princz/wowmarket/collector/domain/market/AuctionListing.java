package com.mia_princz.wowmarket.collector.domain.market;

/**
 * One listing of a snapshot, exactly as the API listed it. Money is in copper. Commodity listings are priced per unit
 * with {@code unitPrice}; realm listings carry a {@code buyout} for the whole listing, a {@code bid}, or both.
 *
 * <p>Fields are nullable on purpose: a listing is recorded as received, and the ingestion checks count and drop the
 * malformed ones instead of the parser failing the whole snapshot on the first bad row.
 *
 * @param timeLeft the raw API value; see {@link TimeLeft#fromApi(String)}
 */
public record AuctionListing(
        Long id, ListedItem item, Integer quantity, Long unitPrice, Long buyout, Long bid, String timeLeft) {}
