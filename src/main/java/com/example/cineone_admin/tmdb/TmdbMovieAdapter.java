package com.example.cineone_admin.tmdb;

import com.example.cineone_admin.dto.sync.NormalizedCatalogDTO.MovieDTO;
import com.example.cineone_admin.enums.MovieStatus;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Chuẩn hóa metadata; trạng thái kinh doanh không suy ra từ lịch phát hành toàn cầu của TMDB. */
@Component
public class TmdbMovieAdapter {
    public record EnrichedMovie(MovieDTO movie, List<TmdbDtos.Genre> genres) {
        public EnrichedMovie { genres = List.copyOf(genres); }
    }
    private final TmdbProperties properties;
    public TmdbMovieAdapter(TmdbProperties properties) { this.properties = properties; }

    public EnrichedMovie normalize(TmdbDtos.MovieDetail detail) {
        try {
            if (detail == null || detail.id() == null || detail.id() <= 0 || detail.genres() == null)
                throw new IllegalArgumentException();
            for (var genre : detail.genres()) {
                if (genre == null || genre.id() == null || genre.id() <= 0 || blank(genre.name()) || genre.name().length() > 100)
                    throw new IllegalArgumentException();
            }
            String director = detail.credits() == null ? null : safe(detail.credits().crew()).stream()
                    .filter(c -> "Director".equals(c.job())).map(TmdbDtos.Crew::name)
                    .filter(n -> !blank(n)).distinct().collect(Collectors.joining(", "));
            String cast = detail.credits() == null ? null : safe(detail.credits().cast()).stream()
                    .sorted(Comparator.comparing(TmdbDtos.Cast::order, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(TmdbDtos.Cast::name).filter(n -> !blank(n)).distinct().collect(Collectors.joining(", "));
            String trailer = detail.videos() == null ? null : safe(detail.videos().results()).stream()
                    .filter(v -> "YouTube".equals(v.site()) && "Trailer".equals(v.type())
                            && v.key() != null && v.key().matches("[A-Za-z0-9_-]+"))
                    .sorted(Comparator.comparing(v -> !Boolean.TRUE.equals(v.official())))
                    .map(v -> "https://www.youtube.com/watch?v=" + v.key()).findFirst().orElse(null);
            String countries = safe(detail.countries()).stream().map(TmdbDtos.Country::name)
                    .filter(n -> !blank(n)).distinct().collect(Collectors.joining(", "));
            String poster = blank(detail.posterPath()) ? null : posterUrl(detail.posterPath());
            var movie = new MovieDTO(detail.id().toString(), detail.title(), nullable(detail.overview()), detail.runtime(),
                    null, nullable(countries), blank(detail.releaseDate()) ? null : LocalDate.parse(detail.releaseDate()),
                    poster, trailer, nullable(director), nullable(cast), MovieStatus.COMING_SOON);
            // MySQL TEXT giới hạn theo byte, không phải số ký tự.
            checkText(movie.description());
            checkText(movie.castInfo());
            return new EnrichedMovie(movie, detail.genres());
        } catch (IllegalArgumentException | NullPointerException | java.time.DateTimeException ex) {
            throw new TmdbException(TmdbException.Kind.INVALID_RESPONSE, "Chi tiết phim TMDB không đáp ứng dữ liệu lưu trữ");
        }
    }

    private String posterUrl(String path) {
        if (!path.matches("/[A-Za-z0-9._/-]+") || path.contains("..")) throw new IllegalArgumentException();
        return properties.getImageBaseUrl().replaceAll("/+$", "") + path;
    }
    private static void checkText(String text) {
        if (text != null && text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65535)
            throw new IllegalArgumentException();
    }
    private static <T> List<T> safe(List<T> values) {
        if (values == null) return List.of();
        if (values.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException();
        return values;
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String nullable(String value) { return blank(value) ? null : value; }
}
