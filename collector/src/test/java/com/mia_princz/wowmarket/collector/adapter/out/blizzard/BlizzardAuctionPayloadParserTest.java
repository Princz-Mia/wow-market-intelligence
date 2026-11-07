package com.mia_princz.wowmarket.collector.adapter.out.blizzard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mia_princz.wowmarket.collector.application.port.out.AuctionPayloadParserPort.MalformedPayloadException;
import com.mia_princz.wowmarket.collector.domain.market.AuctionListing;
import com.mia_princz.wowmarket.collector.domain.market.ListedItem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class BlizzardAuctionPayloadParserTest {

    private final BlizzardAuctionPayloadParser parser =
            new BlizzardAuctionPayloadParser(JsonMapper.builder().build());

    @Test
    void readsCommodityListings() throws IOException {
        List<AuctionListing> listings = parseFixture("commodities.json");

        assertThat(listings).hasSize(3);
        assertThat(listings.getFirst())
                .isEqualTo(
                        new AuctionListing(2000001L, ListedItem.commodity(190396), 200, 1250000L, null, null, "SHORT"));
    }

    @Test
    void readsRealmListingsWithBonusListsPetsAndBothPricingModes() throws IOException {
        List<AuctionListing> listings = parseFixture("connected-realm-1403.json");

        assertThat(listings)
                .containsExactly(
                        new AuctionListing(
                                3000001L,
                                new ListedItem(19019, 13, List.of(6646, 1472), null, null, null, null),
                                1,
                                null,
                                45000000000L,
                                null,
                                "MEDIUM"),
                        new AuctionListing(
                                3000002L,
                                new ListedItem(82800, null, List.of(), 1155, 25, 3, 5),
                                1,
                                null,
                                2000000L,
                                1500000L,
                                "LONG"),
                        new AuctionListing(3000003L, ListedItem.commodity(2589), 20, null, null, 120L, "SHORT"));
    }

    @Test
    void handsOverMalformedListingsWithTheirBadFieldsNull() {
        List<AuctionListing> listings = parse("""
                {"auctions": [
                  {"id": 1, "item": {"id": "x"}, "quantity": -1.5, "unit_price": 99999999999999999999, "time_left": 3},
                  {"id": 2},
                  "not a listing"
                ]}""");

        assertThat(listings)
                .containsExactly(
                        new AuctionListing(
                                1L,
                                new ListedItem(null, null, null, null, null, null, null),
                                null,
                                null,
                                null,
                                null,
                                null),
                        new AuctionListing(2L, null, null, null, null, null, null),
                        new AuctionListing(null, null, null, null, null, null, null));
    }

    @Test
    void acceptsASnapshotWithoutListings() {
        assertThat(parse("{\"auctions\": []}")).isEmpty();
    }

    @Test
    void refusesABodyWithoutAnAuctionsArray() {
        assertThatThrownBy(() -> parse("{\"_links\": {}}"))
                .isInstanceOf(MalformedPayloadException.class)
                .hasMessageContaining("no auctions array");
        assertThatThrownBy(() -> parse("{\"auctions\": {}}"))
                .isInstanceOf(MalformedPayloadException.class)
                .hasMessageContaining("not an array");
        assertThatThrownBy(() -> parse("[]"))
                .isInstanceOf(MalformedPayloadException.class)
                .hasMessageContaining("not a JSON object");
    }

    @Test
    void refusesATruncatedBody() {
        assertThatThrownBy(() -> parse("{\"auctions\": [{\"id\": 1, \"quantity\": 2}, {\"id\""))
                .isInstanceOf(MalformedPayloadException.class)
                .hasMessageContaining("not valid JSON");
    }

    private List<AuctionListing> parseFixture(String name) throws IOException {
        try (InputStream json = getClass().getResourceAsStream("/fixtures/" + name)) {
            List<AuctionListing> listings = new ArrayList<>();
            long count = parser.parse(json, listings::add);
            assertThat(count).isEqualTo(listings.size());
            return listings;
        }
    }

    private List<AuctionListing> parse(String json) {
        List<AuctionListing> listings = new ArrayList<>();
        parser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), listings::add);
        return listings;
    }
}
