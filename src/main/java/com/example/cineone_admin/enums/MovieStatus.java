package com.example.cineone_admin.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;

/** Values defined by the CineOne database design. */
public enum MovieStatus {
    COMING_SOON, SNEAK_SHOW, NOW_SHOWING, END;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static MovieStatus fromJson(JsonNode value) {
        return CatalogEnumJson.parse(value, values(), MovieStatus::name);
    }
}
