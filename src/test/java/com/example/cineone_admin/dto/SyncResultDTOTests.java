package com.example.cineone_admin.dto;

import com.example.cineone_admin.model.RoomType;

import com.example.cineone_admin.dto.sync.*;
import com.example.cineone_admin.enums.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SyncResultDTOTests {
    @Test void reportsFailureWithoutInventingMissingIdentifiers() {
        var errors = new ArrayList<SyncErrorDTO>();
        errors.add(new SyncErrorDTO(ProviderEntityType.CINEMA, null, "Thiếu ID bên ngoài"));
        var result = SyncResultDTO.of("CGV", NormalizedCatalogDTO.empty(), errors);
        errors.clear();
        assertEquals(0, result.successCount());
        assertEquals(1, result.failureCount());
        assertFalse(result.isSuccessful());
        assertThrows(UnsupportedOperationException.class, () -> result.errors().clear());
    }

    @Test void rejectsInconsistentCountsAndWrongReferenceTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> new SyncResultDTO("CGV", NormalizedCatalogDTO.empty(), 1, 0, List.of()));
        var movie = new ProviderReferenceDTO("TMDB", ProviderEntityType.MOVIE, "1022789");
        assertThrows(IllegalArgumentException.class,
                () -> new NormalizedCatalogDTO.RoomDTO("room-1", movie, "Phòng 1", null, null));
    }
    @Test void countsPartialBatchAndPreservesExternalNamespace() {
        var ref = new ProviderReferenceDTO("CGV", ProviderEntityType.CINEMA, "cinema-1");
        var room = new NormalizedCatalogDTO.RoomDTO("room-1", ref, "Phòng 1", RoomType.STANDARD, RoomStatus.ACTIVE);
        var data = new NormalizedCatalogDTO(List.of(), List.of(room), List.of(), List.of(), List.of());
        var result = SyncResultDTO.of("CGV", data,
                List.of(new SyncErrorDTO(ProviderEntityType.ROOM, "room-2", "Thông tin phòng chiếu không hợp lệ")));
        assertEquals(1, result.successCount());
        assertEquals(1, result.failureCount());
        assertFalse(result.isSuccessful());
        assertEquals("cinema-1", result.data().rooms().getFirst().cinema().externalId());
        assertThrows(UnsupportedOperationException.class, () -> result.data().rooms().clear());
    }

    @Test void rejectsInvalidTimesAndPricesBeforePersistence() {
        var movie = new ProviderReferenceDTO("TMDB", ProviderEntityType.MOVIE, "1022789");
        var room = new ProviderReferenceDTO("CGV", ProviderEntityType.ROOM, "room-1");
        var start = LocalDateTime.of(2026, 10, 18, 19, 0);
        assertThrows(IllegalArgumentException.class, () -> new NormalizedCatalogDTO.ShowtimeDTO(
                "show-1", movie, room, start, start, new BigDecimal("85000"), ShowtimeStatus.OPEN));
        assertThrows(IllegalArgumentException.class, () -> new NormalizedCatalogDTO.ShowtimeDTO(
                "show-1", movie, room, start, start.plusHours(2), new BigDecimal("-1"), ShowtimeStatus.OPEN));
        var showtime = new NormalizedCatalogDTO.ShowtimeDTO("show-1", movie, room, start,
                start.plusHours(2), new BigDecimal("85000.50"), ShowtimeStatus.OPEN);
        assertEquals("TMDB", showtime.movie().providerCode());
        assertEquals(new BigDecimal("85000.50"), showtime.basePrice());
    }
}
