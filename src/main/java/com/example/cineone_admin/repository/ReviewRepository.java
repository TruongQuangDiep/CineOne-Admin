package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long>, JpaSpecificationExecutor<Review> {
    List<Review> findByMovie_MovieIdAndStatusOrderByCreatedAtDescReviewIdDesc(Long movieId, String status);
}
