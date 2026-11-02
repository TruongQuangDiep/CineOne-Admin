package com.example.cineone_admin.controller.api;

import com.example.cineone_admin.dto.ai.FaqAnswerResponse;
import com.example.cineone_admin.dto.ai.FaqQuestionRequest;
import com.example.cineone_admin.dto.ai.ReviewSummaryResponse;
import com.example.cineone_admin.dto.common.ApiResponse;
import com.example.cineone_admin.exception.AiExtractionException;
import com.example.cineone_admin.service.FaqRagService;
import com.example.cineone_admin.service.ReviewSummarizerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AiApiController {

    private final FaqRagService faqRagService;
    private final ReviewSummarizerService reviewSummarizerService;

    @PostMapping("/api/v1/ai/faq")
    public ResponseEntity<ApiResponse<FaqAnswerResponse>> answerFaq(
            @RequestBody(required = false) FaqQuestionRequest request) {
        if (request == null) {
            throw new AiExtractionException(AiExtractionException.Kind.INVALID_INPUT);
        }
        FaqAnswerResponse response = faqRagService.answer(request.getQuestion(), request.getCategory());
        String message = response.answered()
                ? "Giải đáp câu hỏi thành công."
                : "Chưa có thông tin phù hợp trong Knowledge Base.";
        return ResponseEntity.ok(ApiResponse.success(response, message));
    }

    @GetMapping({"/api/v1/movies/{movieId}/reviews/summary", "/api/v1/ai/movies/{movieId}/review-summary"})
    public ResponseEntity<ApiResponse<ReviewSummaryResponse>> summarizeMovieReviews(
            @PathVariable Long movieId) {
        ReviewSummaryResponse response = reviewSummarizerService.summarize(movieId);
        String message = response.summarized()
                ? "Tóm tắt đánh giá phim thành công."
                : "Chưa có đủ nhận xét hiển thị để tóm tắt.";
        return ResponseEntity.ok(ApiResponse.success(response, message));
    }
}

