package com.example.cineone_admin.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewEligibilityResponse {
    private boolean eligible;
    private boolean alreadyReviewed;
    private Integer existingRating;
    private String existingContent;
    private String message;
}

