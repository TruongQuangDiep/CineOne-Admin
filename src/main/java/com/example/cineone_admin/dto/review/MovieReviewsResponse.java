package com.example.cineone_admin.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieReviewsResponse {
    private Long movieId;
    private String movieTitle;
    private double averageRating;
    private int reviewCount;
    private List<MovieReviewItemResponse> reviews;
}

