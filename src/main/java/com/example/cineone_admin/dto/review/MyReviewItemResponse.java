package com.example.cineone_admin.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyReviewItemResponse {
    private Long reviewId;
    private Long movieId;
    private String movieTitle;
    private String posterUrl;
    private int rating;
    private String content;
    private String status;
    private String createdAt;
}

