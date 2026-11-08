package com.mia_princz.wowmarket.collector.domain.market;

import java.util.List;

/**
 * The {@code item} object of a listing, as listed. Commodity listings carry only the id. Realm listings may add the
 * drop context, bonus lists and, for battle pets, the species, level, quality and breed. Any field can be missing in a
 * malformed payload; the ingestion checks decide what to do about it.
 *
 * @param bonusLists never null, empty when the listing has none, in API order
 */
public record ListedItem(
        Integer id,
        Integer context,
        List<Integer> bonusLists,
        Integer petSpeciesId,
        Integer petLevel,
        Integer petQualityId,
        Integer petBreedId) {

    public ListedItem {
        bonusLists = bonusLists == null ? List.of() : List.copyOf(bonusLists);
    }

    /** A commodity: only the id is known. */
    public static ListedItem commodity(int id) {
        return new ListedItem(id, null, List.of(), null, null, null, null);
    }
}
