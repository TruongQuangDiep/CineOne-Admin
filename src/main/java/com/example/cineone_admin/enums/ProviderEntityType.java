package com.example.cineone_admin.enums;

import com.example.cineone_admin.entity.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;

/** Các giá trị cố định được lưu trong ProviderMapping.entityType. */
public enum ProviderEntityType {
    CINEMA_BRAND(CinemaBrand.class), CINEMA(Cinema.class), ROOM(Room.class),
    SEAT(Seat.class), MOVIE(Movie.class), SHOWTIME(Showtime.class), FNB_ITEM(FnbItem.class);

    private final Class<?> entityClass;

    ProviderEntityType(Class<?> entityClass) { this.entityClass = entityClass; }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ProviderEntityType fromJson(JsonNode value) {
        return CatalogEnumJson.parse(value, values(), ProviderEntityType::name);
    }

    public Class<?> getEntityClass() { return entityClass; }
}
