package com.example.cineone_admin.dto;

import com.example.cineone_admin.dto.fnb.FnbItemRequest;
import com.example.cineone_admin.dto.movie.MovieRequest;
import com.example.cineone_admin.dto.movie.MovieResponse;
import com.example.cineone_admin.dto.cinema.CinemaRequest;
import com.example.cineone_admin.dto.cinema.CinemaResponse;
import com.example.cineone_admin.dto.fnb.FnbItemResponse;
import com.example.cineone_admin.dto.room.RoomResponse;
import com.example.cineone_admin.dto.room.RoomRequest;
import com.example.cineone_admin.dto.showtime.ShowtimeRequest;
import com.example.cineone_admin.dto.showtime.ShowtimeResponse;
import com.example.cineone_admin.model.RoomType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class CatalogJsonTests {
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void roundTripsLocalDateTimeAndExactMoneyWithoutValidatorProperty() {
        ShowtimeRequest r = CatalogValidationTests.showtime();
        String json = mapper.writeValueAsString(r);
        assertTrue(json.contains("\"startTime\":\"2020-01-01T20:00:00\""));
        assertFalse(json.contains("timeRangeValid"));
        ShowtimeRequest copy = mapper.readValue(json, ShowtimeRequest.class);
        assertEquals(r.getStartTime(), copy.getStartTime());
        assertEquals(r.getEndTime(), copy.getEndTime());
        assertEquals(r.getBasePrice(), copy.getBasePrice());
        FnbItemRequest fnb = CatalogValidationTests.fnb();
        fnb.setPrice(new BigDecimal("9999999999.99"));
        assertEquals(fnb.getPrice(), mapper.readValue(mapper.writeValueAsString(fnb), FnbItemRequest.class).getPrice());
    }

    @ParameterizedTest
    @ValueSource(strings = {"01/01/2020 20:00", "2020-01-01", "2020-02-30T20:00:00", "2020-01-01T25:00:00", "2020-01-01T20:00:00Z", "2020-01-01T20:00:00+07:00", "2020-01-01T20:00:00.123"})
    void rejectsMalformedOrOffsetDatetime(String value) {
        assertThrows(JacksonException.class, () -> mapper.readValue("{\"startTime\":\"" + value + "\"}", ShowtimeRequest.class));
    }

    @Test
    void serializesResponseTimeAsLocalStringAndPriceAsNumber() {
        ShowtimeResponse r = new ShowtimeResponse();
        r.setStartTime(CatalogValidationTests.showtime().getStartTime());
        r.setBasePrice(new BigDecimal("9999999999.99"));
        var json = mapper.readTree(mapper.writeValueAsString(r));
        assertEquals("2020-01-01T20:00:00", json.get("startTime").asString());
        assertTrue(json.get("basePrice").isNumber());
        assertEquals(0, new BigDecimal("9999999999.99").compareTo(json.get("basePrice").decimalValue()));
    }

    @Test
    void usesSchemaEnumValuesIncluding4dxAndRejectsUnknownValues() {
        RoomRequest r = mapper.readValue("{\"roomType\":\"4DX\"}", RoomRequest.class);
        assertEquals(RoomType.FOUR_DX, r.getRoomType());
        assertEquals("4DX", r.getRoomType().getDatabaseValue());
        assertTrue(mapper.writeValueAsString(r).contains("\"roomType\":\"4DX\""));
        assertThrows(JacksonException.class, () -> mapper.readValue("{\"status\":\"UNKNOWN\"}", ShowtimeRequest.class));
        assertThrows(JacksonException.class, () -> mapper.readValue("{\"status\":\"open\"}", ShowtimeRequest.class));
        assertEquals(RoomType.of("VIP"), mapper.readValue("{\"roomType\":\"VIP\"}", RoomRequest.class).getRoomType());
    }

    @Test
    void roundTripsHistoricalDateAndGenreIds() {
        MovieRequest r = CatalogValidationTests.movie();
        r.setReleaseDate(java.time.LocalDate.of(1990, 2, 28));
        String json = mapper.writeValueAsString(r);
        assertTrue(json.contains("\"releaseDate\":\"1990-02-28\""));
        MovieRequest copy = mapper.readValue(json, MovieRequest.class);
        assertEquals(r.getReleaseDate(), copy.getReleaseDate());
        assertEquals(r.getGenreIds(), copy.getGenreIds());
        MovieResponse response = new MovieResponse();
        response.setReleaseDate(r.getReleaseDate());
        assertEquals("1990-02-28", mapper.readTree(mapper.writeValueAsString(response)).get("releaseDate").asString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"28/02/1990", "1990-02-29", "2020-13-01", "2020-01-01T00:00:00", "2020-01-01Z"})
    void rejectsMalformedDate(String value) {
        assertThrows(JacksonException.class, () -> mapper.readValue("{\"releaseDate\":\"" + value + "\"}", MovieRequest.class));
    }

    @Test
    void rejectsUnknownAndNumericStatusAcrossAllCatalogs() {
        for (Class<?> type : java.util.List.of(MovieRequest.class, CinemaRequest.class,
                RoomRequest.class, ShowtimeRequest.class, FnbItemRequest.class)) {
            assertThrows(JacksonException.class, () -> mapper.readValue("{\"status\":\"UNKNOWN\"}", type));
            assertThrows(JacksonException.class, () -> mapper.readValue("{\"status\":0}", type));
        }
    }

    @Test
    void preservesCoordinateDecimalPrecisionInJsonWithoutChangingEntityMapping() {
        CinemaRequest r = CatalogValidationTests.cinema();
        r.setLatitude(new BigDecimal("10.776912345"));
        r.setLongitude(new BigDecimal("106.700912345"));
        CinemaRequest copy = mapper.readValue(mapper.writeValueAsString(r), CinemaRequest.class);
        assertEquals(r.getLatitude(), copy.getLatitude());
        assertEquals(r.getLongitude(), copy.getLongitude());
    }

    @Test
    void serializesReadOnlyRoomDetailsAndRelationNamesWithoutEntityCycles() {
        RoomResponse room = new RoomResponse();
        room.setRoomId(3L);
        room.setCinemaId(2L);
        room.setRoomType(RoomType.FOUR_DX);
        room.setSeatCount(1L);
        room.setSeats(java.util.List.of(new RoomResponse.SeatSummary(4L, "A", 1, "NORMAL", "ACTIVE")));
        CinemaResponse cinema = new CinemaResponse();
        cinema.setCinemaId(2L);
        cinema.setBrandName("Thương hiệu");
        cinema.setRoomCount(1L);
        cinema.setRooms(java.util.List.of(room));
        var json = mapper.readTree(mapper.writeValueAsString(cinema));
        assertEquals("4DX", json.get("rooms").get(0).get("roomType").asString());
        assertEquals("A", json.get("rooms").get(0).get("seats").get(0).get("seatRow").asString());
        assertFalse(json.get("rooms").get(0).has("cinema"));
        FnbItemResponse fnb = new FnbItemResponse();
        fnb.setPrice(new BigDecimal("65000.01"));
        assertEquals(fnb.getPrice(), mapper.readValue(mapper.writeValueAsString(fnb), FnbItemResponse.class).getPrice());
    }
}
