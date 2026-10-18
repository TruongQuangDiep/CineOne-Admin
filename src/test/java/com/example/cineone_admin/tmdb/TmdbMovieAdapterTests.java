package com.example.cineone_admin.tmdb;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.cineone_admin.tmdb.TmdbDtos.*;

class TmdbMovieAdapterTests {
    private final TmdbMovieAdapter adapter = new TmdbMovieAdapter(new TmdbProperties());

    static MovieDetail detail(long id, String poster, List<Genre> genres) {
        return new MovieDetail(id, "Phim", "Mô tả", 120, "2026-10-18", poster,
                List.of(new Country("VN", "Vietnam")), genres,
                new Credits(List.of(new Cast("B", 2), new Cast("A", 0)),
                        List.of(new Crew("Đạo diễn", "Director"), new Crew("Biên kịch", "Writer"))),
                new Videos(List.of(new Video("teaser", "YouTube", "Teaser", true),
                        new Video("unofficial", "YouTube", "Trailer", false),
                        new Video("official", "YouTube", "Trailer", true))));
    }

    @Test void mapsPosterDirectorOrderedCastAndOfficialTrailer() {
        var result = adapter.normalize(detail(42, "/poster.jpg", List.of(new Genre(28L, "Action"))));
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", result.movie().posterUrl());
        assertEquals("Đạo diễn", result.movie().director());
        assertEquals("A, B", result.movie().castInfo());
        assertEquals("https://www.youtube.com/watch?v=official", result.movie().trailerUrl());
        assertEquals("42", result.movie().externalId());
        assertEquals(28L, result.genres().getFirst().id());
    }

    @Test void absentOptionalMetadataIsNotFabricated() {
        var result = adapter.normalize(new MovieDetail(42L, "Phim", "", 120, "", null,
                null, List.of(), null, null));
        assertNull(result.movie().posterUrl());
        assertNull(result.movie().trailerUrl());
        assertNull(result.movie().director());
        assertNull(result.movie().castInfo());
        assertNull(result.movie().releaseDate());
    }

    @Test void rejectsMissingDurationInvalidDateAndInvalidGenre() {
        for (var invalid : List.of(
                new MovieDetail(42L, "Phim", null, null, "", null, null, List.of(), null, null),
                new MovieDetail(42L, "Phim", null, 120, "bad-date", null, null, List.of(), null, null),
                detail(42, "/poster.jpg", List.of(new Genre(28L, " "))))) {
            assertEquals(TmdbException.Kind.INVALID_RESPONSE,
                    assertThrows(TmdbException.class, () -> adapter.normalize(invalid)).getKind());
        }
    }
}
