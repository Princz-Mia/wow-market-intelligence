package com.mia_princz.wowmarket.collector.adapter.out.blizzard;

import com.mia_princz.wowmarket.collector.application.port.out.AuctionPayloadParserPort;
import com.mia_princz.wowmarket.collector.domain.market.AuctionListing;
import com.mia_princz.wowmarket.collector.domain.market.ListedItem;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Streams the {@code auctions} array of a commodities or connected-realm snapshot token by token. Both payloads share
 * one listing shape; commodity listings simply carry fewer fields. Unknown fields, such as {@code modifiers} or
 * {@code _links}, are skipped.
 */
public class BlizzardAuctionPayloadParser implements AuctionPayloadParserPort {

    private static final AuctionListing UNREADABLE = new AuctionListing(null, null, null, null, null, null, null);

    private final JsonMapper json;

    public BlizzardAuctionPayloadParser(JsonMapper json) {
        this.json = json;
    }

    @Override
    public long parse(InputStream body, Consumer<AuctionListing> listings) {
        try (JsonParser parser = json.createParser(body)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw malformed("the body is not a JSON object", null);
            }
            long count = -1;
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                String name = parser.currentName();
                JsonToken value = parser.nextToken();
                if ("auctions".equals(name)) {
                    if (value != JsonToken.START_ARRAY) {
                        throw malformed("auctions is not an array", null);
                    }
                    count = auctions(parser, listings);
                } else {
                    parser.skipChildren();
                }
            }
            if (count < 0) {
                throw malformed("the body has no auctions array", null);
            }
            return count;
        } catch (JacksonException e) {
            throw malformed("the body is not valid JSON: " + e.getOriginalMessage(), e);
        }
    }

    private static long auctions(JsonParser parser, Consumer<AuctionListing> listings) {
        long count = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() == JsonToken.START_OBJECT) {
                listings.accept(listing(parser));
            } else {
                parser.skipChildren();
                listings.accept(UNREADABLE);
            }
            count++;
        }
        return count;
    }

    private static AuctionListing listing(JsonParser parser) {
        Long id = null;
        ListedItem item = null;
        Integer quantity = null;
        Long unitPrice = null;
        Long buyout = null;
        Long bid = null;
        String timeLeft = null;
        while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
            String name = parser.currentName();
            parser.nextToken();
            switch (name) {
                case "id" -> id = longValue(parser);
                case "item" -> item = item(parser);
                case "quantity" -> quantity = intValue(parser);
                case "unit_price" -> unitPrice = longValue(parser);
                case "buyout" -> buyout = longValue(parser);
                case "bid" -> bid = longValue(parser);
                case "time_left" -> timeLeft = stringValue(parser);
                default -> parser.skipChildren();
            }
        }
        return new AuctionListing(id, item, quantity, unitPrice, buyout, bid, timeLeft);
    }

    private static ListedItem item(JsonParser parser) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            parser.skipChildren();
            return null;
        }
        Integer id = null;
        Integer context = null;
        List<Integer> bonusLists = List.of();
        Integer petSpeciesId = null;
        Integer petLevel = null;
        Integer petQualityId = null;
        Integer petBreedId = null;
        while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
            String name = parser.currentName();
            parser.nextToken();
            switch (name) {
                case "id" -> id = intValue(parser);
                case "context" -> context = intValue(parser);
                case "bonus_lists" -> bonusLists = intArray(parser);
                case "pet_species_id" -> petSpeciesId = intValue(parser);
                case "pet_level" -> petLevel = intValue(parser);
                case "pet_quality_id" -> petQualityId = intValue(parser);
                case "pet_breed_id" -> petBreedId = intValue(parser);
                default -> parser.skipChildren();
            }
        }
        return new ListedItem(id, context, bonusLists, petSpeciesId, petLevel, petQualityId, petBreedId);
    }

    private static List<Integer> intArray(JsonParser parser) {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            parser.skipChildren();
            return List.of();
        }
        List<Integer> values = new ArrayList<>(4);
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            Integer value = intValue(parser);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    /** The value if it is an integer that fits; otherwise null, with the value skipped. */
    private static Long longValue(JsonParser parser) {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            parser.skipChildren();
            return null;
        }
        try {
            return parser.getLongValue();
        } catch (InputCoercionException e) {
            return null;
        }
    }

    private static Integer intValue(JsonParser parser) {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            parser.skipChildren();
            return null;
        }
        try {
            return parser.getIntValue();
        } catch (InputCoercionException e) {
            return null;
        }
    }

    private static String stringValue(JsonParser parser) {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            parser.skipChildren();
            return null;
        }
        return parser.getString();
    }

    private static MalformedPayloadException malformed(String message, Throwable cause) {
        return new MalformedPayloadException(message, cause);
    }
}
