package com.example.cineone_admin.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class TmdbDtos {
    private TmdbDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Genre(Long id, String name) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Genres(List<Genre> genres) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MovieSummary(Long id, String title, String overview,
            @JsonProperty("poster_path") String posterPath,
            @JsonProperty("release_date") String releaseDate,
            @JsonProperty("genre_ids") List<Long> genreIds) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(Integer page, @JsonProperty("total_pages") Integer totalPages,
            @JsonProperty("total_results") Long totalResults, List<MovieSummary> results) {
        public boolean hasNext() { return page < Math.min(totalPages, 500); }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MovieDetail(Long id, String title, String overview, Integer runtime,
            @JsonProperty("release_date") String releaseDate,
            @JsonProperty("poster_path") String posterPath,
            @JsonProperty("production_countries") List<Country> countries,
            List<Genre> genres, Credits credits, Videos videos) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Country(@JsonProperty("iso_3166_1") String code, String name) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Credits(List<Cast> cast, List<Crew> crew) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cast(String name, Integer order) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Crew(String name, String job) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Videos(List<Video> results) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Video(String key, String site, String type, Boolean official) {}
}
