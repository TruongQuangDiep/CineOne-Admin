package com.example.cineone_admin.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;

/** Values defined by the CineOne database design. */
public enum FnbStatus {
    ACTIVE, INACTIVE, OUT_OF_STOCK;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static FnbStatus fromJson(JsonNode value) {
        return CatalogEnumJson.parse(value, values(), FnbStatus::name);
    }
}
