package com.example.cineone_admin.tmdb;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TmdbMovieServiceTests {
    private final TmdbClient client = mock(TmdbClient.class);
    private final TmdbMovieStore store = mock(TmdbMovieStore.class);
    private final TmdbMovieService service = new TmdbMovieService(client,
            new TmdbMovieAdapter(new TmdbProperties()), store);

    @Test void timeoutNeverReachesPersistence() {
        when(client.details(42)).thenThrow(new TmdbException(TmdbException.Kind.TIMEOUT, "Timeout"));
        assertThrows(TmdbException.class, () -> service.importMovie(42));
        verifyNoInteractions(store);
    }

    @Test void pageContinuesAfterIndividualApiFailureAndReportsIt() {
        var a = new TmdbDtos.MovieSummary(42L, null, null, null, null, null);
        var b = new TmdbDtos.MovieSummary(43L, null, null, null, null, null);
        when(client.movies(TmdbClient.MovieList.POPULAR, 1))
                .thenReturn(new TmdbDtos.Page(1, 2, 3L, List.of(a, b, b)));
        when(client.details(42)).thenThrow(new TmdbException(TmdbException.Kind.TIMEOUT, "Timeout"));
        when(client.details(43)).thenReturn(TmdbMovieAdapterTests.detail(43, null, List.of()));
        when(store.save(any())).thenReturn(7L);
        var result = service.importMovies(TmdbClient.MovieList.POPULAR, 1);
        assertEquals(List.of(7L), result.movieIds());
        assertEquals(42L, result.failures().getFirst().tmdbId());
        assertEquals("TIMEOUT", result.failures().getFirst().code());
        assertTrue(result.hasNext());
        verify(client, times(1)).details(43);
        verify(store, times(1)).save(any());
    }
}
