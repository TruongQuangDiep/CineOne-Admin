package com.example.cineone_admin.sync.mock;

import com.example.cineone_admin.dto.sync.*;
import com.example.cineone_admin.enums.*;
import com.example.cineone_admin.sync.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import static com.example.cineone_admin.enums.ProviderEntityType.*;
import static org.junit.jupiter.api.Assertions.*;

class MockCinemaAdapterTests {
    private static final Set<ProviderEntityType> ALL = Set.of(CINEMA_BRAND, CINEMA, ROOM, SEAT, SHOWTIME, FNB_ITEM);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T17:30:00Z"), ZoneOffset.UTC);
    @TempDir Path temp;

    private MockCinemaAdapter adapter(String provider, String root, Clock clock) {
        return new MockCinemaAdapter(provider, provider, Set.of("CGV", "BETA", "MEGAGS").contains(provider),
                new MockSourceReader(new DefaultResourceLoader(), root), clock);
    }
    private MockCinemaAdapter adapter(String provider) { return adapter(provider, "classpath:mock/data_source/", CLOCK); }
    private SyncRequestDTO request(Set<ProviderEntityType> scope) { return new SyncRequestDTO(scope, SyncMode.FULL, null, null, null); }

    @ParameterizedTest
    @CsvSource({"CGV,86,516,8,8", "BETA,22,134,34,10", "MEGAGS,2,12,17,10",
            "GALAXY,31,186,37,10", "LOTTE,43,258,38,10", "CINESTAR,10,60,37,10"})
    void readsAllSourcesAndPreservesReferences(String code, int cinemas, int rooms, int shows, int foodPerCinema) {
        var result = adapter(code).sync(request(ALL));
        assertTrue(result.isSuccessful(), result.errors().toString());
        var data = result.data();
        assertEquals(cinemas, data.cinemas().size());
        assertEquals(rooms, data.rooms().size());
        assertEquals(shows, data.showtimes().size());
        assertEquals(cinemas * foodPerCinema, data.fnbItems().size());
        assertEquals(data.size(), result.successCount());
        assertEquals(1, data.cinemaBrands().size());
        var cinemaIds = new HashSet<>(data.cinemas().stream().map(c -> c.externalId()).toList());
        var roomIds = new HashSet<>(data.rooms().stream().map(r -> r.externalId()).toList());
        assertEquals(rooms, roomIds.size());
        assertTrue(data.rooms().stream().allMatch(r -> cinemaIds.contains(r.cinema().externalId())));
        assertTrue(data.seats().stream().allMatch(s -> roomIds.contains(s.room().externalId())));
        assertEquals(data.seats().size(), data.seats().stream().map(s -> s.externalId()).distinct().count());
        assertEquals(data.fnbItems().size(), data.fnbItems().stream().map(f -> f.externalId()).distinct().count());
        for (var show : data.showtimes()) {
            assertTrue(roomIds.contains(show.room().externalId()));
            assertEquals("TMDB", show.movie().providerCode());
            assertFalse(show.startTime().toLocalDate().isBefore(LocalDate.of(2026, 10, 21)));
            assertTrue(show.endTime().isAfter(show.startTime()));
        }
        assertEquals(result, adapter(code).sync(request(ALL)), "Đọc lại cùng ngày phải giữ định danh");
    }

    @Test void explicitTemplatesResolveSameRoomTypeAndCinemasWithoutShows() {
        var data = adapter("BETA").sync(request(ALL)).data();
        var rooms = data.rooms().stream().filter(r -> r.cinema().externalId().equals("BETA_QUANG_TRUNG")).toList();
        assertEquals(8, rooms.size());
        for (var room : rooms) {
            int number = Integer.parseInt(room.name().substring(room.name().lastIndexOf(' ') + 1));
            assertEquals(number % 2 == 1 ? 80 : 100,
                    data.seats().stream().filter(s -> s.room().externalId().equals(room.externalId())).count());
        }
        assertEquals(data.cinemas().size(), data.rooms().stream().map(r -> r.cinema().externalId()).distinct().count());
    }

    @Test void dateRangeIsInclusiveAndDoesNotRebaseDayOffsets() {
        var day = LocalDate.of(2026, 10, 22);
        var result = adapter("BETA").sync(new SyncRequestDTO(Set.of(SHOWTIME), SyncMode.FULL, day, day, null));
        assertTrue(result.isSuccessful(), result.errors().toString());
        assertFalse(result.data().showtimes().isEmpty());
        assertTrue(result.data().showtimes().stream().allMatch(s -> s.startTime().toLocalDate().equals(day)));
        assertTrue(result.data().rooms().isEmpty());
        var empty = adapter("CGV").sync(new SyncRequestDTO(Set.of(SHOWTIME), SyncMode.FULL, day.plusYears(1), day.plusYears(1), null));
        assertTrue(empty.isSuccessful());
        assertEquals(0, empty.successCount());
    }

    @Test void rollingMockUsesDifferentShowtimeIdsOnNextDay() {
        var today = adapter("CGV").sync(request(Set.of(SHOWTIME))).data().showtimes().getFirst();
        var tomorrow = adapter("CGV", "classpath:mock/data_source/", Clock.offset(CLOCK, Duration.ofDays(1)))
                .sync(request(Set.of(SHOWTIME))).data().showtimes().getFirst();
        assertNotEquals(today.externalId(), tomorrow.externalId());
        assertEquals(today.startTime().plusDays(1), tomorrow.startTime());
    }

    @Test void factoryRegistersSixStrategiesWithoutDatabase() {
        try (var context = new AnnotationConfigApplicationContext(MockAdapterConfiguration.class, CinemaSyncAdapterFactory.class)) {
            var factory = context.getBean(CinemaSyncAdapterFactory.class);
            assertEquals(6, context.getBeansOfType(CinemaSyncAdapter.class).size());
            for (String code : List.of("CGV", "BETA", "MEGAGS", "GALAXY", "LOTTE", "CINESTAR"))
                assertEquals(code, factory.getAdapter(code).providerCode());
        }
    }

    @Test void rejectsUnsupportedModeAndEntities() {
        assertFalse(adapter("CGV").sync(new SyncRequestDTO(Set.of(CINEMA), SyncMode.INCREMENTAL, null, null, CLOCK.instant())).isSuccessful());
        assertFalse(adapter("CGV").sync(request(Set.of(MOVIE))).isSuccessful());
        assertFalse(adapter("CGV").sync(request(Set.of(GENRE))).isSuccessful());
    }

    private MockCinemaAdapter copied(String code) throws Exception {
        String folder = code.toLowerCase(Locale.ROOT);
        Path source = Paths.get(Objects.requireNonNull(getClass().getResource("/mock/data_source/" + folder)).toURI());
        Path target = Files.createDirectories(temp.resolve(folder));
        try (var files = Files.list(source)) {
            for (Path file : files.toList()) Files.copy(file, target.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
        return adapter(code, temp.toUri().toString(), CLOCK);
    }
    private void edit(String file, UnaryOperator<String> change) throws Exception {
        Path path = temp.resolve(file);
        Files.writeString(path, change.apply(Files.readString(path)));
    }
    private void assertFailed(SyncResultDTO result) {
        assertFalse(result.isSuccessful());
        assertEquals(0, result.successCount());
        assertEquals(1, result.failureCount());
        assertFalse(result.errors().getFirst().message().isBlank());
    }

    private void assertPartial(SyncResultDTO result) {
        assertFalse(result.isSuccessful());
        assertTrue(result.successCount() > 0, "Valid rows must survive a malformed record");
        assertTrue(result.failureCount() > 0);
        assertEquals(result.data().size(), result.successCount());
        assertFalse(result.errors().getFirst().message().isBlank());
    }

    @Test void serviceReferenceDateOverridesAdapterClockWithoutAddingOffsetTwice() {
        var adapter = adapter("CGV");
        var normal = adapter.sync(request(Set.of(SHOWTIME))).data().showtimes();
        var shifted = adapter.sync(request(Set.of(SHOWTIME)), LocalDate.of(2026, 10, 25)).data().showtimes();
        assertEquals(normal.size(), shifted.size());
        for (int i = 0; i < normal.size(); i++) {
            assertEquals(normal.get(i).startTime().plusDays(4), shifted.get(i).startTime());
            assertEquals(normal.get(i).endTime().plusDays(4), shifted.get(i).endTime());
            assertNotEquals(normal.get(i).externalId(), shifted.get(i).externalId());
        }
    }

    @Test void invalidShowtimeIsIdentifiedAndOtherSevenRowsRemain() throws Exception {
        var adapter = copied("CGV");
        edit("cgv/showtimes.json", s -> s.replaceFirst("09:30", "25:00"));
        var result = adapter.sync(request(Set.of(SHOWTIME)));
        assertEquals(7, result.successCount()); assertEquals(1, result.failureCount());
        assertEquals(SHOWTIME, result.errors().getFirst().entityType());
        assertNotNull(result.errors().getFirst().externalId());
        assertTrue(result.data().showtimes().stream().noneMatch(s ->
                s.externalId().startsWith(result.errors().getFirst().externalId() + "::")));
    }

    @Test void missingRequiredJsonFieldIsAnIndividualRecordError() throws Exception {
        var adapter = copied("CGV");
        edit("cgv/cinemas.json", s -> s.replaceFirst("\"address\"", "\"missing_address\""));
        var result = adapter.sync(request(Set.of(CINEMA)));
        assertEquals(85, result.successCount()); assertEquals(1, result.failureCount());
    }

    @Test void malformedJsonAndMissingFileFailWithoutPartialSnapshot() throws Exception {
        var adapter = copied("CGV");
        edit("cgv/cinemas.json", s -> "{");
        assertFailed(adapter.sync(request(ALL)));
        Files.delete(temp.resolve("cgv/cinemas.json"));
        assertFailed(adapter.sync(request(Set.of(CINEMA))));
    }

    @Test void scopeDoesNotReadUnrelatedBrokenFiles() throws Exception {
        var adapter = copied("CGV");
        edit("cgv/showtimes.json", s -> "broken");
        edit("cgv/rooms.json", s -> "broken");
        assertTrue(adapter.sync(request(Set.of(FNB_ITEM))).isSuccessful());
        assertTrue(adapter.sync(request(Set.of(CINEMA))).isSuccessful());
    }

    @ParameterizedTest @ValueSource(strings = {"cinema_id", "template_code", "room_type"})
    void rejectsBrokenRoomReferences(String field) throws Exception {
        var adapter = copied("BETA");
        edit("beta/rooms.json", s -> s.replaceFirst("\"" + field + "\": \"[^\"]+\"", "\"" + field + "\": \"UNKNOWN\""));
        assertPartial(adapter.sync(request(ALL)));
    }

    @Test void rejectsDuplicateRoomIds() throws Exception {
        var adapter = copied("BETA");
        edit("beta/rooms.json", s -> s.replace("BETA_THANH_XUAN_ROOM_02", "BETA_THANH_XUAN_ROOM_01"));
        assertPartial(adapter.sync(request(ALL)));
    }

    @Test void rejectsDuplicateSeats() throws Exception {
        var adapter = copied("CGV");
        edit("cgv/seat_templates.json", s -> s.replaceFirst("\"number\": 2", "\"number\": 1"));
        assertFailed(adapter.sync(request(Set.of(SEAT))));
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "1.234", "10000000000"})
    void rejectsInvalidPrices(String price) throws Exception {
        var adapter = copied("GALAXY");
        edit("galaxy/fnb.csv", s -> s.replaceFirst(",85000,", "," + price + ","));
        assertPartial(adapter.sync(request(Set.of(FNB_ITEM))));
    }

    @ParameterizedTest @ValueSource(strings = {"25:00", "9:30", "09:99"})
    void rejectsInvalidShowtimeSlots(String slot) throws Exception {
        var adapter = copied("CGV");
        edit("cgv/showtimes.json", s -> s.replace("09:30", slot));
        assertPartial(adapter.sync(request(Set.of(SHOWTIME))));
    }

    @Test void computesEndTimeAcrossMidnight() throws Exception {
        var adapter = copied("CGV");
        edit("cgv/showtimes.json", s -> s.replace("09:30", "23:30"));
        var show = adapter.sync(request(Set.of(SHOWTIME))).data().showtimes().getFirst();
        assertEquals(LocalDate.of(2026, 10, 22), show.endTime().toLocalDate());
        assertEquals(110, Duration.between(show.startTime(), show.endTime()).toMinutes());
    }

    @Test void csvSupportsBomCommasQuotesAndMultilineText() throws Exception {
        var adapter = copied("GALAXY");
        edit("galaxy/fnb.csv", s -> "\uFEFFexternal_id,name,price,description,image_url\r\n"
                + "F1,\"Combo, \"\"đôi\"\"\",100.25,\"Dòng 1\nDòng 2\",\r\n");
        var result = adapter.sync(request(Set.of(FNB_ITEM)));
        assertTrue(result.isSuccessful(), result.errors().toString());
        var food = result.data().fnbItems().getFirst();
        assertEquals("Combo, \"đôi\"", food.name());
        assertEquals("Dòng 1\nDòng 2", food.description());
        assertEquals("100.25", food.price().toPlainString());
    }

    @ParameterizedTest @ValueSource(strings = {
            "external_id,name,name,price,description,image_url\nF1,a,b,1,d,i\n",
            "external_id,name,price,description,image_url\nF1,a,1,d,i,EXTRA\n",
            "external_id,name,description,image_url\nF1,a,d,i\n",
            "external_id,name,price,description,image_url\nF1,\"unterminated,1,d,i\n"})
    void rejectsMalformedCsv(String csv) throws Exception {
        var adapter = copied("GALAXY");
        edit("galaxy/fnb.csv", s -> csv);
        assertFailed(adapter.sync(request(Set.of(FNB_ITEM))));
    }

    @Test void rejectsWrongJsonProviderAndDuplicateProperties() throws Exception {
        var adapter = copied("CGV");
        edit("cgv/cinemas.json", s -> s.replace("\"provider_code\": \"CGV\"", "\"provider_code\": \"OTHER\""));
        assertFailed(adapter.sync(request(Set.of(CINEMA))));
        copied("CGV");
        edit("cgv/cinemas.json", s -> s.replace("\"provider_code\": \"CGV\"", "\"provider_code\": \"CGV\", \"provider_code\": \"CGV\""));
        assertFailed(adapter.sync(request(Set.of(CINEMA))));
    }

    @Test void jsonMoneyRemainsExactAndDoesNotRoundAnInvalidScale() throws Exception {
        var adapter = copied("CGV");
        edit("cgv/fnb.json", s -> s.replaceFirst("95000", "9999999999.99"));
        var result = adapter.sync(request(Set.of(FNB_ITEM)));
        assertTrue(result.isSuccessful(), result.errors().toString());
        assertEquals("9999999999.99", result.data().fnbItems().getFirst().price().toPlainString());
        edit("cgv/fnb.json", s -> s.replaceFirst("9999999999.99", "9999999999.991"));
        assertPartial(adapter.sync(request(Set.of(FNB_ITEM))));
    }

    @Test void rejectsBooleanStatusInsteadOfDefaultingToActive() throws Exception {
        var adapter = copied("CGV");
        edit("cgv/fnb.json", s -> s.replaceFirst("\"status\": \"ACTIVE\"", "\"status\": true"));
        assertPartial(adapter.sync(request(Set.of(FNB_ITEM))));
    }
}
