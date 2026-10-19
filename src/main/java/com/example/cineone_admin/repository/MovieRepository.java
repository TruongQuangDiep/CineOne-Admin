package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, Long> {}
