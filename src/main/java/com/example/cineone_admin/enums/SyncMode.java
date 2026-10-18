package com.example.cineone_admin.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;

/** Đồng bộ toàn bộ phạm vi đã chọn hoặc lấy thay đổi từ mốc thời gian. */
public enum SyncMode {
    FULL, INCREMENTAL;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static SyncMode fromJson(JsonNode value) {
        return CatalogEnumJson.parse(value, values(), SyncMode::name);
    }
}
