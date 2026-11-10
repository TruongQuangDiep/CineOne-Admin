package com.example.cineone_admin.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewApiRequest {
    private Long movieId;
    private Integer rating;
    private String content;
}

