package com.example.cineone_admin.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;

/** Trạng thái ghế theo thiết kế cơ sở dữ liệu. */
public enum SeatStatus {
    ACTIVE, INACTIVE, MAINTENANCE;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static SeatStatus fromJson(JsonNode value) {
        return CatalogEnumJson.parse(value, values(), SeatStatus::name);
    }
}
