package com.example.cineone_admin.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;

/** Chỉ chuyển trạng thái ánh xạ khi bên gọi yêu cầu tường minh. */
public enum ProviderMappingStatus {
    ACTIVE, INACTIVE, ERROR;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ProviderMappingStatus fromJson(JsonNode value) {
        return CatalogEnumJson.parse(value, values(), ProviderMappingStatus::name);
    }
}
