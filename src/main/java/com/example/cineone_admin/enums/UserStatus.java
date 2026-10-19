package com.example.cineone_admin.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;

public enum UserStatus {
    ACTIVE, LOCKED, INACTIVE;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static UserStatus fromJson(JsonNode value) {
        return CatalogEnumJson.parse(value, values(), UserStatus::name);
    }
}