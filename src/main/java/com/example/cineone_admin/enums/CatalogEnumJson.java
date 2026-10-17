package com.example.cineone_admin.enums;

import java.util.function.Function;
import tools.jackson.databind.JsonNode;

/** Keep the catalog JSON contract strict without changing the application mapper. */
final class CatalogEnumJson {
    private CatalogEnumJson() {}

    static <E extends Enum<E>> E parse(JsonNode input, E[] values, Function<E, String> wireName) {
        if (input == null || input.isNull()) {
            return null; // Required request fields are checked by Bean Validation.
        }
        if (input.isString()) {
            for (E value : values) {
                if (wireName.apply(value).equals(input.stringValue())) {
                    return value;
                }
            }
        }
        throw new IllegalArgumentException("Expected a supported " + values[0].getDeclaringClass().getSimpleName() + " string");
    }
}
