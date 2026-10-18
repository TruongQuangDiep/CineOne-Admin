package com.example.cineone_admin.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;

/** Loại ghế theo thiết kế cơ sở dữ liệu. */
public enum SeatType {
    NORMAL, VIP, SWEETBOX;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static SeatType fromJson(JsonNode value) {
        return CatalogEnumJson.parse(value, values(), SeatType::name);
    }
}
