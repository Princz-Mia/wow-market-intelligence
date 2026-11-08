package com.mia_princz.wowmarket.collector.domain.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ItemVariantKeyTest {

    private static ListedItem gear(int id, List<Integer> bonusLists, Integer context) {
        return new ListedItem(id, context, bonusLists, null, null, null, null);
    }

    private static ListedItem pet(int species, int level, int quality, int breed) {
        return new ListedItem(82800, null, List.of(), species, level, quality, breed);
    }

    @Test
    void keepsTheCanonicalFormAndItsHashStable() {
        ListedItem sword = gear(19019, List.of(6646, 1472), 13);

        assertThat(ItemVariantKey.canonical(sword)).isEqualTo("i=19019;b=1472,6646;s=;l=");
        assertThat(ItemVariantKey.of(sword).value()).isEqualTo(8447923991564662311L);
        assertThat(ItemVariantKey.of(pet(1155, 25, 3, 5)).value()).isEqualTo(532260183192562009L);
    }

    @Test
    void ignoresTheOrderOfBonusLists() {
        assertThat(ItemVariantKey.of(gear(19019, List.of(6646, 1472), null)))
                .isEqualTo(ItemVariantKey.of(gear(19019, List.of(1472, 6646), null)));
    }

    @Test
    void separatesItemsThatDifferInBonusLists() {
        assertThat(ItemVariantKey.of(gear(19019, List.of(1472), null)))
                .isNotEqualTo(ItemVariantKey.of(gear(19019, List.of(1472, 6646), null)));
    }

    @Test
    void ignoresTheDropContext() {
        assertThat(ItemVariantKey.of(gear(19019, List.of(1472), 13)))
                .isEqualTo(ItemVariantKey.of(gear(19019, List.of(1472), 26)));
    }

    @Test
    void separatesPetsBySpeciesAndLevelButNotByQualityOrBreed() {
        ItemVariantKey levelOne = ItemVariantKey.of(pet(1155, 1, 3, 5));

        assertThat(levelOne).isNotEqualTo(ItemVariantKey.of(pet(1155, 25, 3, 5)));
        assertThat(levelOne).isNotEqualTo(ItemVariantKey.of(pet(1156, 1, 3, 5)));
        assertThat(levelOne).isEqualTo(ItemVariantKey.of(pet(1155, 1, 2, 7)));
    }

    @Test
    void keysACommodityByItsIdAlone() {
        assertThat(ItemVariantKey.of(ListedItem.commodity(190396)))
                .isEqualTo(ItemVariantKey.of(gear(190396, List.of(), null)))
                .isNotEqualTo(ItemVariantKey.of(ListedItem.commodity(190397)));
    }

    @Test
    void needsAnItemId() {
        assertThatThrownBy(() -> ItemVariantKey.of(new ListedItem(null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
