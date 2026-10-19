package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.MovieGenre;
import com.example.cineone_admin.entity.MovieGenreId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieGenreRepository extends JpaRepository<MovieGenre, MovieGenreId> {}
