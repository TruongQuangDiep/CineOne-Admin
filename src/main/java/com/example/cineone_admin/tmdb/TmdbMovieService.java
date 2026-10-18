package com.example.cineone_admin.tmdb;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;

/** Điểm gọi cho controller/job đồng bộ. Xem danh sách không tự động ghi vào database. */
@Service
public class TmdbMovieService {
    public record ImportFailure(Long tmdbId, String code, String message) {}
    public record ImportPage(int page, int totalPages, boolean hasNext, List<Long> movieIds,
            List<ImportFailure> failures) {
        public ImportPage { movieIds = List.copyOf(movieIds); failures = List.copyOf(failures); }
    }
    private final TmdbClient client;
    private final TmdbMovieAdapter adapter;
    private final TmdbMovieStore store;

    public TmdbMovieService(TmdbClient client, TmdbMovieAdapter adapter, TmdbMovieStore store) {
        this.client = client;
        this.adapter = adapter;
        this.store = store;
    }

    public Long importMovie(long tmdbId) {
        return store.save(adapter.normalize(client.details(tmdbId)));
    }

    public ImportPage importMovies(TmdbClient.MovieList list, int page) {
        return importPage(client.movies(list, page));
    }

    public ImportPage importSearch(String query, int page) {
        return importPage(client.search(query, page));
    }

    private ImportPage importPage(TmdbDtos.Page page) {
        List<Long> saved = new ArrayList<>();
        List<ImportFailure> failures = new ArrayList<>();
        var ids = new LinkedHashSet<Long>();
        page.results().forEach(movie -> ids.add(movie.id()));
        for (Long id : ids) {
            try {
                saved.add(importMovie(id));
            } catch (TmdbException ex) {
                failures.add(new ImportFailure(id, ex.getKind().name(), ex.getMessage()));
            }
            // Lỗi DB/cấu hình provider phải nổi lên cho bên gọi, không giả báo thành công.
        }
        return new ImportPage(page.page(), page.totalPages(), page.hasNext(), saved, failures);
    }
}
