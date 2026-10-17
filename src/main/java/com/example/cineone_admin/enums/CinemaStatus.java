package com.example.cineone_admin.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;

/** Values defined by the CineOne database design. */
public enum CinemaStatus {
    ACTIVE, INACTIVE;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CinemaStatus fromJson(JsonNode value) {
        return CatalogEnumJson.parse(value, values(), CinemaStatus::name);
    }
}
