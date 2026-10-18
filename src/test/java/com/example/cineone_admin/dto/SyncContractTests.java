package com.example.cineone_admin.dto;

import com.example.cineone_admin.dto.sync.*;
import com.example.cineone_admin.enums.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class SyncContractTests {
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test void preservesScopedDateRangeAndImmutableEntityTypes() {
        var types = new HashSet<>(Set.of(ProviderEntityType.SHOWTIME));
        var date = LocalDate.of(2026, 10, 18);
        var request = new SyncRequestDTO(types, SyncMode.FULL, date, date.plusDays(1), null);
        types.clear();
        assertEquals(Set.of(ProviderEntityType.SHOWTIME), request.entityTypes());
        assertEquals(request, mapper.readValue(mapper.writeValueAsString(request), SyncRequestDTO.class));
        assertThrows(UnsupportedOperationException.class, () -> request.entityTypes().clear());
    }

    @Test void requiresExplicitIncrementalWatermark() {
        var types = Set.of(ProviderEntityType.MOVIE);
        assertThrows(IllegalArgumentException.class,
                () -> new SyncRequestDTO(types, SyncMode.INCREMENTAL, null, null, null));
        Instant watermark = Instant.parse("2026-10-17T00:00:00Z");
        var request = new SyncRequestDTO(types, SyncMode.INCREMENTAL, null, null, watermark);
        assertEquals(request, mapper.readValue(mapper.writeValueAsString(request), SyncRequestDTO.class));
        assertThrows(IllegalArgumentException.class,
                () -> new SyncRequestDTO(types, SyncMode.FULL, null, null, watermark));
    }

    @Test void rejectsIncompleteOrContradictoryScopes() {
        var date = LocalDate.of(2026, 10, 18);
        assertThrows(IllegalArgumentException.class,
                () -> new SyncRequestDTO(Set.of(), SyncMode.FULL, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SyncRequestDTO(Set.of(ProviderEntityType.SHOWTIME), SyncMode.FULL, date, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SyncRequestDTO(Set.of(ProviderEntityType.SHOWTIME), SyncMode.FULL, date, date.minusDays(1), null));
        assertThrows(IllegalArgumentException.class,
                () -> new SyncRequestDTO(Set.of(ProviderEntityType.SEAT), SyncMode.FULL, date, date, null));
    }

    @Test void catalogIncludesMoviesAndBrandsInResultCountsAndJson() {
        var movie = new NormalizedCatalogDTO.MovieDTO("1022789", "Phim kiểm thử", null, 110,
                "P", "Việt Nam", LocalDate.of(2026, 10, 18), null, null, null, null, MovieStatus.NOW_SHOWING);
        var brand = new NormalizedCatalogDTO.CinemaBrandDTO("brand", "Hãng rạp", null, CinemaBrandStatus.ACTIVE);
        var catalog = new NormalizedCatalogDTO(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(movie), List.of(brand));
        assertEquals(2, SyncResultDTO.of("CGV", catalog, List.of()).successCount());
        assertEquals(catalog, mapper.readValue(mapper.writeValueAsString(catalog), NormalizedCatalogDTO.class));
        assertThrows(IllegalArgumentException.class, () -> new NormalizedCatalogDTO.MovieDTO(
                "x", "Phim", null, 0, null, null, null, null, null, null, null, MovieStatus.NOW_SHOWING));
    }

    @Test void seatEnumsRoundTripUsingSchemaValues() {
        var room = new ProviderReferenceDTO("CGV", ProviderEntityType.ROOM, "room-1");
        var seat = new NormalizedCatalogDTO.SeatDTO("seat-1", room, "A", 1, SeatType.SWEETBOX, SeatStatus.MAINTENANCE);
        assertEquals(seat, mapper.readValue(mapper.writeValueAsString(seat), NormalizedCatalogDTO.SeatDTO.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "true", "\"vip\"", "\"UNKNOWN\""})
    void rejectsInvalidSeatEnumJson(String value) {
        assertThrows(JacksonException.class, () -> mapper.readValue(value, SeatType.class));
        assertThrows(JacksonException.class, () -> mapper.readValue(value, SeatStatus.class));
    }
}
