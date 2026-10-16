package com.example.cineone_admin.enums;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;

public enum RoomType {
    STANDARD, IMAX,
    @JsonProperty("4DX")
    FOUR_DX,
    SCREENX;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static RoomType fromJson(JsonNode value) {
        return CatalogEnumJson.parse(value, values(), RoomType::getDatabaseValue);
    }

    /** Database value; FOUR_DX cannot be represented by Enum.name(). */
    public String getDatabaseValue() {
        return this == FOUR_DX ? "4DX" : name();
    }
}
