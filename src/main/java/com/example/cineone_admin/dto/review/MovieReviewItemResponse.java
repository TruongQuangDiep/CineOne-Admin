package com.example.cineone_admin.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieReviewItemResponse {
    private Long reviewId;
    private Long userId;
    private String userName;
    private String userEmail;
    private int rating;
    private String content;
    private String createdAt;
}

