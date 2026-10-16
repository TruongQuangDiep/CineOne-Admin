package com.example.cineone_admin.dto.movie;

import com.example.cineone_admin.enums.MovieStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MovieResponse {
    private Long movieId;
    private String title;
    private String description;
    private Integer duration;
    private String ageRating;
    private String country;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu-MM-dd")
    private LocalDate releaseDate;
    private String posterUrl;
    private String trailerUrl;
    private String director;
    private String castInfo;
    private MovieStatus status;
    private List<GenreSummary> genres;

    public record GenreSummary(Long genreId, String name) {}

}
