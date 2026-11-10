package com.example.cineone_admin.controller.api;

import com.example.cineone_admin.dto.common.ApiResponse;
import com.example.cineone_admin.dto.review.*;
import com.example.cineone_admin.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewApiController {

    private final ReviewService reviewService;

    @GetMapping("/movie/{movieId}")
    public ResponseEntity<ApiResponse<MovieReviewsResponse>> getMovieReviews(@PathVariable Long movieId) {
        MovieReviewsResponse data = reviewService.getMovieReviews(movieId);
        return ResponseEntity.ok(ApiResponse.<MovieReviewsResponse>builder()
                .status(200)
                .message("Lấy danh sách đánh giá phim thành công.")
                .data(data)
                .build());
    }

    @GetMapping("/my-reviews")
    public ResponseEntity<ApiResponse<List<MyReviewItemResponse>>> getMyReviews(Authentication authentication) {
        List<MyReviewItemResponse> data = reviewService.getMyReviews(authentication);
        return ResponseEntity.ok(ApiResponse.<List<MyReviewItemResponse>>builder()
                .status(200)
                .message("Lấy danh sách đánh giá của tôi thành công.")
                .data(data)
                .build());
    }

    @GetMapping("/eligibility/{movieId}")
    public ResponseEntity<ApiResponse<ReviewEligibilityResponse>> checkEligibility(
            @PathVariable Long movieId,
            Authentication authentication) {
        ReviewEligibilityResponse data = reviewService.checkEligibility(movieId, authentication);
        return ResponseEntity.ok(ApiResponse.<ReviewEligibilityResponse>builder()
                .status(200)
                .message(data.getMessage())
                .data(data)
                .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MovieReviewItemResponse>> createReview(
            @RequestBody CreateReviewApiRequest request,
            Authentication authentication) {
        MovieReviewItemResponse data = reviewService.createOrUpdateReview(request, authentication);
        return ResponseEntity.ok(ApiResponse.<MovieReviewItemResponse>builder()
                .status(200)
                .message("Gửi đánh giá phim thành công.")
                .data(data)
                .build());
    }
}

