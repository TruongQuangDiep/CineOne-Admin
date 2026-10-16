package com.example.cineone_admin.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;

/** Values defined by the CineOne database design. */
public enum RoomStatus {
    ACTIVE, INACTIVE, MAINTENANCE;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static RoomStatus fromJson(JsonNode value) {
        return CatalogEnumJson.parse(value, values(), RoomStatus::name);
    }
}
