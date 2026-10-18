package com.example.cineone_admin.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;

/** Trạng thái hãng rạp theo thiết kế cơ sở dữ liệu. */
public enum CinemaBrandStatus {
    ACTIVE, INACTIVE;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CinemaBrandStatus fromJson(JsonNode value) {
        return CatalogEnumJson.parse(value, values(), CinemaBrandStatus::name);
    }
}
