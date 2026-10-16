package com.example.cineone_admin.dto.movie;

import com.example.cineone_admin.dto.validation.Utf8Size;
import com.example.cineone_admin.enums.MovieStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/** Create/full replacement. Service maps genre IDs through MovieGenre. */
@Getter
@Setter
public class MovieRequest {
    @NotBlank
    @Size(max = 255)
    private String title;

    @Utf8Size
    private String description;

    @NotNull
    @Positive
    private Integer duration;

    @Size(max = 20)
    private String ageRating;

    @Size(max = 100)
    private String country;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
    private LocalDate releaseDate;

    @Size(max = 500)
    private String posterUrl;

    @Size(max = 500)
    private String trailerUrl;

    @Size(max = 255)
    private String director;

    @Utf8Size
    private String castInfo;

    @NotNull
    private MovieStatus status;

    @NotEmpty
    private Set<@NotNull @Positive Long> genreIds;
}
