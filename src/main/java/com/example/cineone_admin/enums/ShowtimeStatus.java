package com.example.cineone_admin.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;

/** Values defined by the CineOne database design. */
public enum ShowtimeStatus {
    OPEN, CLOSED, CANCELLED, ENDED;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ShowtimeStatus fromJson(JsonNode value) {
        return CatalogEnumJson.parse(value, values(), ShowtimeStatus::name);
    }
}
