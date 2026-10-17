package com.example.cineone_admin.dto;

import com.example.cineone_admin.dto.fnb.FnbItemRequest;
import com.example.cineone_admin.dto.movie.MovieRequest;
import com.example.cineone_admin.dto.cinema.CinemaRequest;
import com.example.cineone_admin.dto.room.RoomRequest;
import com.example.cineone_admin.dto.showtime.ShowtimeRequest;
import com.example.cineone_admin.enums.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CatalogValidationTests {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    private Set<String> errors(Object request) {
        return VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    static ShowtimeRequest showtime() {
        ShowtimeRequest r = new ShowtimeRequest();
        r.setMovieId(1L);
        r.setRoomId(2L);
        r.setStartTime(LocalDateTime.of(2020, 1, 1, 20, 0));
        r.setEndTime(LocalDateTime.of(2020, 1, 1, 22, 0));
        r.setBasePrice(new BigDecimal("95000.50"));
        r.setStatus(ShowtimeStatus.OPEN);
        return r;
    }

    static FnbItemRequest fnb() {
        FnbItemRequest r = new FnbItemRequest();
        r.setBrandId(1L);
        r.setCinemaId(2L);
        r.setName("Combo bắp nước");
        r.setPrice(new BigDecimal("65000.00"));
        r.setStatus(FnbStatus.ACTIVE);
        return r;
    }

    @Test
    void missingRequiredFieldsAreReportedWithoutDatabase() {
        assertEquals(Set.of("movieId", "roomId", "startTime", "endTime", "basePrice", "status"), errors(new ShowtimeRequest()));
        assertEquals(Set.of("brandId", "cinemaId", "name", "price", "status"), errors(new FnbItemRequest()));
        assertEquals(Set.of("cinemaId", "name", "roomType", "status"), errors(new RoomRequest()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "10000000000.00", "1.001", "0.000", "9999999999.999"})
    void rejectsInvalidMoneyForBothPrices(String price) {
        ShowtimeRequest showtime = showtime();
        showtime.setBasePrice(new BigDecimal(price));
        assertEquals(Set.of("basePrice"), errors(showtime));
        FnbItemRequest fnb = fnb();
        fnb.setPrice(new BigDecimal(price));
        assertEquals(Set.of("price"), errors(fnb));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "0.01", "9999999999.99"})
    void acceptsMoneyBoundaries(String price) {
        ShowtimeRequest showtime = showtime();
        showtime.setBasePrice(new BigDecimal(price));
        assertTrue(errors(showtime).isEmpty());
        FnbItemRequest fnb = fnb();
        fnb.setPrice(new BigDecimal(price));
        assertTrue(errors(fnb).isEmpty());
    }

    @Test
    void enforcesTimeOrderingButAllowsHistoricalAndOvernightShowtimes() {
        ShowtimeRequest r = showtime();
        assertTrue(errors(r).isEmpty());
        r.setEndTime(r.getStartTime());
        assertEquals(Set.of("timeRangeValid"), errors(r));
        r.setEndTime(r.getStartTime().minusSeconds(1));
        assertEquals(Set.of("timeRangeValid"), errors(r));
        r.setEndTime(r.getStartTime().plusDays(1));
        assertTrue(errors(r).isEmpty());
        r.setStartTime(null);
        assertEquals(Set.of("startTime"), errors(r));
    }

    @Test
    void validatesRoomNameAndPositiveForeignKey() {
        RoomRequest r = new RoomRequest();
        r.setCinemaId(1L);
        r.setRoomType(RoomType.FOUR_DX);
        r.setStatus(RoomStatus.ACTIVE);
        r.setName("x".repeat(100));
        assertTrue(errors(r).isEmpty());
        r.setName("x".repeat(101));
        assertEquals(Set.of("name"), errors(r));
        r.setName(" \t\n");
        r.setCinemaId(0L);
        assertEquals(Set.of("name", "cinemaId"), errors(r));
    }

    @Test
    void validatesFnbLengthsNullableFieldsAndTextByteLimit() {
        FnbItemRequest r = fnb();
        assertTrue(errors(r).isEmpty());
        r.setName("x".repeat(150));
        r.setImageUrl("x".repeat(500));
        r.setDescription("a".repeat(65535));
        assertTrue(errors(r).isEmpty());
        r.setName("x".repeat(151));
        r.setImageUrl("x".repeat(501));
        r.setDescription("a".repeat(65536));
        assertEquals(Set.of("name", "imageUrl", "description"), errors(r));
        r = fnb();
        r.setDescription("€".repeat(21845));
        assertTrue(errors(r).isEmpty());
        r.setDescription("€".repeat(21846));
        assertEquals(Set.of("description"), errors(r));
        r = fnb();
        r.setName("  ");
        r.setBrandId(-1L);
        r.setCinemaId(0L);
        assertEquals(Set.of("name", "brandId", "cinemaId"), errors(r));
    }

    static MovieRequest movie() {
        MovieRequest r = new MovieRequest();
        r.setTitle("Phim thử nghiệm");
        r.setDuration(120);
        r.setStatus(MovieStatus.COMING_SOON);
        r.setGenreIds(Set.of(1L));
        return r;
    }

    static CinemaRequest cinema() {
        CinemaRequest r = new CinemaRequest();
        r.setBrandId(1L);
        r.setName("Rạp thử nghiệm");
        r.setAddress("Địa chỉ rạp");
        r.setStatus(CinemaStatus.ACTIVE);
        return r;
    }

    @Test
    void validatesMovieRequiredFieldsDurationAndGenreIds() {
        assertEquals(Set.of("title", "duration", "status", "genreIds"), errors(new MovieRequest()));
        MovieRequest r = movie();
        assertTrue(errors(r).isEmpty());
        r.setTitle(" \t ");
        r.setDuration(0);
        r.setGenreIds(Set.of());
        assertEquals(Set.of("title", "duration", "genreIds"), errors(r));
        r = movie();
        r.setDuration(-1);
        assertEquals(Set.of("duration"), errors(r));
        r = movie();
        r.setGenreIds(Set.of(0L));
        assertTrue(errors(r).stream().anyMatch(p -> p.startsWith("genreIds")));
        java.util.HashSet<Long> genres = new java.util.HashSet<>();
        genres.add(null);
        r.setGenreIds(genres);
        assertTrue(errors(r).stream().anyMatch(p -> p.startsWith("genreIds")));
    }

    @Test
    void validatesMovieStringBoundariesWithoutRequiringOptionalMetadata() {
        MovieRequest r = movie();
        r.setTitle("x".repeat(255));
        r.setAgeRating("x".repeat(20));
        r.setCountry("x".repeat(100));
        r.setDirector("x".repeat(255));
        r.setPosterUrl("x".repeat(500));
        r.setTrailerUrl("x".repeat(500));
        r.setReleaseDate(java.time.LocalDate.of(1990, 1, 1));
        assertTrue(errors(r).isEmpty());
        r.setTitle(r.getTitle() + "x");
        r.setAgeRating(r.getAgeRating() + "x");
        r.setCountry(r.getCountry() + "x");
        r.setDirector(r.getDirector() + "x");
        r.setPosterUrl(r.getPosterUrl() + "x");
        r.setTrailerUrl(r.getTrailerUrl() + "x");
        r.setDescription("a".repeat(65536));
        r.setCastInfo("a".repeat(65536));
        assertEquals(Set.of("title", "ageRating", "country", "director", "posterUrl", "trailerUrl", "description", "castInfo"), errors(r));
    }

    @Test
    void validatesCinemaRequiredFieldsAndStringLimits() {
        assertEquals(Set.of("brandId", "name", "address", "status"), errors(new CinemaRequest()));
        CinemaRequest r = cinema();
        assertTrue(errors(r).isEmpty());
        r.setName("x".repeat(150));
        r.setAddress("x".repeat(255));
        r.setPhone("x".repeat(20));
        assertTrue(errors(r).isEmpty());
        r.setName(r.getName() + "x");
        r.setAddress(r.getAddress() + "x");
        r.setPhone(r.getPhone() + "x");
        assertEquals(Set.of("name", "address", "phone"), errors(r));
        r = cinema();
        r.setName(" ");
        r.setAddress("\t");
        r.setBrandId(-1L);
        assertEquals(Set.of("name", "address", "brandId"), errors(r));
    }

    @Test
    void validatesCoordinateRangesAndAllowsNullCoordinates() {
        CinemaRequest r = cinema();
        assertTrue(errors(r).isEmpty());
        r.setLatitude(new BigDecimal("-90"));
        r.setLongitude(new BigDecimal("-180"));
        assertTrue(errors(r).isEmpty());
        r.setLatitude(new BigDecimal("90"));
        r.setLongitude(new BigDecimal("180"));
        assertTrue(errors(r).isEmpty());
        r.setLatitude(new BigDecimal("90.0000001"));
        r.setLongitude(new BigDecimal("-180.0000001"));
        assertEquals(Set.of("latitude", "longitude"), errors(r));
        r.setLatitude(new BigDecimal("-90.0000001"));
        r.setLongitude(new BigDecimal("180.0000001"));
        assertEquals(Set.of("latitude", "longitude"), errors(r));
    }
}
