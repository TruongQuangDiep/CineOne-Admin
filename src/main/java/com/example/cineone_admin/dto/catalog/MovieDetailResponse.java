package com.example.cineone_admin.dto.catalog;

import com.example.cineone_admin.entity.Movie;
import com.example.cineone_admin.service.CatalogService;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieDetailResponse {
    private Long movieId;
    private String title;
    private String poster;
    private String posterUrl;
    private String trailer;
    private String trailerUrl;
    private Integer duration;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu-MM-dd")
    private LocalDate releaseDate;

    private String genres;
    private Double rating;
    private String ageRating;
    private String country;
    private String director;
    private String castInfo;
    private String description;
    private String status;

    public static MovieDetailResponse from(CatalogService.MovieRow row, Double rating) {
        Movie m = row.movie();
        String normalizedGenres = "—".equals(row.genres()) || row.genres() == null ? "" : row.genres();
        return MovieDetailResponse.builder()
                .movieId(m.getMovieId())
                .title(m.getTitle())
                .poster(m.getPosterUrl())
                .posterUrl(m.getPosterUrl())
                .trailer(m.getTrailerUrl())
                .trailerUrl(m.getTrailerUrl())
                .duration(m.getDuration())
                .releaseDate(m.getReleaseDate())
                .genres(normalizedGenres)
                .rating(rating != null ? rating : 0.0)
                .ageRating(m.getAgeRating())
                .country(m.getCountry())
                .director(m.getDirector())
                .castInfo(m.getCastInfo())
                .description(m.getDescription())
                .status(m.getStatus())
                .build();
    }
}

