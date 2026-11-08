package com.mia_princz.wowmarket.collector.domain.market;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;

/**
 * Identifies a product rather than an item id. The same item with different bonus lists (item level, sockets, tertiary
 * stats) or a caged battle pet of a different species or level is a different product, and prices are only comparable
 * within one variant.
 *
 * <p>The key is built from the item id, the bonus lists sorted ascending, the pet species and the pet level. The drop
 * context and the {@code modifiers} are left out on purpose: they record how and at which player level an item dropped,
 * and including them would split identical goods into many variants. Commodities carry only an item id, so their key
 * depends on the id alone.
 *
 * <p>The value is the first eight bytes of the SHA-256 of the canonical form, for example
 * {@code i=19019;b=1472,6646;s=;l=}. Stored data depends on it, so the canonical form must not change.
 */
public record ItemVariantKey(long value) {

    public static ItemVariantKey of(ListedItem item) {
        if (item.id() == null) {
            throw new IllegalArgumentException("a variant needs an item id");
        }
        return new ItemVariantKey(firstEightBytes(sha256(canonical(item))));
    }

    static String canonical(ListedItem item) {
        String bonusLists =
                item.bonusLists().stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        return "i=" + item.id() + ";b=" + bonusLists + ";s=" + orEmpty(item.petSpeciesId()) + ";l="
                + orEmpty(item.petLevel());
    }

    private static String orEmpty(Integer value) {
        return value == null ? "" : value.toString();
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JDK provides SHA-256", e);
        }
    }

    private static long firstEightBytes(byte[] digest) {
        return ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
    }
}
