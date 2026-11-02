package com.example.cineone_admin.dto.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewSummaryResponse(
        @JsonProperty("movie_id") Long movieId,
        @JsonProperty("movie_title") String movieTitle,
        @JsonProperty("review_count") int reviewCount,
        @JsonProperty("summarized_review_count") int summarizedReviewCount,
        @JsonProperty("average_rating") Double averageRating,
        boolean summarized,
        String summary,
        List<String> highlights) {}

