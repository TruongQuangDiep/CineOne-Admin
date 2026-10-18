package com.example.cineone_admin.dto;

import com.example.cineone_admin.dto.room.RoomRequest;
import com.example.cineone_admin.entity.converter.RoomTypeConverter;
import com.example.cineone_admin.model.RoomType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class RoomTypeTests {
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @ParameterizedTest
    @ValueSource(strings = {"STANDARD", "IMAX", "4DX", "SCREENX", "CINEX", "SUPERPLEX", "VIP_PREMIUM", "FUTURE_ROOM_2030"})
    void acceptsSourceAndFutureTypesWithoutAddingConstants(String code) {
        var type = RoomType.of(code);
        var converter = new RoomTypeConverter();
        assertEquals(code, converter.convertToDatabaseColumn(type));
        assertEquals(type, converter.convertToEntityAttribute(code));
        assertEquals(type, mapper.readValue(mapper.writeValueAsString(type), RoomType.class));
        assertEquals(type, mapper.readValue("{\"roomType\":\"" + code + "\"}", RoomRequest.class).getRoomType());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "true", "[]", "{}", "\"\"", "\" IMAX\"", "\"IMAX \""})
    void rejectsMalformedValuesWithoutSilentlyNormalizing(String json) {
        assertThrows(JacksonException.class, () -> mapper.readValue(json, RoomType.class));
    }

    @Test void enforcesStorageLengthAndKeeps4dxWireValue() {
        assertEquals("4DX", RoomType.FOUR_DX.getDatabaseValue());
        assertThrows(IllegalArgumentException.class, () -> RoomType.of("X".repeat(51)));
        assertThrows(IllegalArgumentException.class, () -> RoomType.of("VIP\nROOM"));
    }
}
