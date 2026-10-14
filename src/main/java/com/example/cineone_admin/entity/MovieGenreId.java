package com.example.cineone_admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import java.io.Serializable;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
public class MovieGenreId implements Serializable {
    @Column(name = "movie_id")
    private Long movieId;

    @Column(name = "genre_id")
    private Long genreId;
}