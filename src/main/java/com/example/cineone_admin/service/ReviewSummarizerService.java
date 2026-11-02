package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.ai.ReviewSummaryResponse;
import com.example.cineone_admin.entity.Movie;
import com.example.cineone_admin.entity.Review;
import com.example.cineone_admin.exception.AiExtractionException;
import com.example.cineone_admin.exception.ResourceNotFoundException;
import com.example.cineone_admin.repository.MovieRepository;
import com.example.cineone_admin.repository.ReviewRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly = true)
public class ReviewSummarizerService {
    public static final int DEFAULT_MAX_REVIEWS = 50;
    public static final String NO_REVIEWS_MESSAGE =
            "Chưa có đủ nhận xét hiển thị của người xem để tóm tắt cho phim này.";

    private final CriteriaGenerator generator;
    private final MovieRepository movieRepository;
    private final ReviewRepository reviewRepository;
    private final int maxReviews;
    private final String systemPrompt;
    private final Map<String, Object> schema;
    private final SearchInputGuard outputGuard = new SearchInputGuard();
    private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    @Autowired
    public ReviewSummarizerService(CriteriaGenerator generator,
                                   MovieRepository movieRepository,
                                   ReviewRepository reviewRepository) {
        this(generator, movieRepository, reviewRepository, DEFAULT_MAX_REVIEWS);
    }

    @SuppressWarnings("unchecked")
    public ReviewSummarizerService(CriteriaGenerator generator,
                                   MovieRepository movieRepository,
                                   ReviewRepository reviewRepository,
                                   int maxReviews) {
        if (maxReviews < 1 || maxReviews > 200) {
            throw new IllegalArgumentException("maxReviews phải từ 1 đến 200.");
        }
        this.generator = generator;
        this.movieRepository = movieRepository;
        this.reviewRepository = reviewRepository;
        this.maxReviews = maxReviews;
        try {
            this.systemPrompt = readResource("ai/review-summary-system-prompt.txt");
            this.schema = mapper.readValue(readResource("ai/review-summary-response.schema.json"), Map.class);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Không thể đọc hợp đồng tóm tắt đánh giá AI.");
        }
    }

    public ReviewSummaryResponse summarize(Long movieId) {
        if (movieId == null || movieId <= 0) {
            throw new AiExtractionException(AiExtractionException.Kind.INVALID_INPUT);
        }

        Movie movie = movieRepository.findById(movieId).orElseThrow(
                () -> new ResourceNotFoundException("Không tìm thấy phim với ID: " + movieId));
        String movieTitle = movie.getTitle() == null ? "" : movie.getTitle().strip();

        List<Review> rawReviews = reviewRepository
                .findByMovie_MovieIdAndStatusOrderByCreatedAtDescReviewIdDesc(movieId, "VISIBLE");

        int visibleCount = 0;
        long ratingSum = 0;
        List<Map<String, Object>> reviewItems = new ArrayList<>();

        if (rawReviews != null) {
            for (Review review : rawReviews) {
                if (review == null
                        || review.getStatus() == null
                        || !"VISIBLE".equalsIgnoreCase(review.getStatus().strip())
                        || review.getRating() == null
                        || review.getRating() < 1
                        || review.getRating() > 5) {
                    continue;
                }
                visibleCount++;
                ratingSum += review.getRating();

                if (reviewItems.size() < maxReviews && review.getContent() != null && !review.getContent().isBlank()) {
                    String safeContent = FaqRagService.sanitizeContextText(review.getContent(), 1000);
                    if (!safeContent.isBlank()) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("rating", review.getRating().intValue());
                        item.put("content", safeContent);
                        reviewItems.add(item);
                    }
                }
            }
        }

        double averageRating = visibleCount == 0
                ? 0.0
                : BigDecimal.valueOf((double) ratingSum / visibleCount)
                        .setScale(1, RoundingMode.HALF_UP)
                        .doubleValue();

        if (reviewItems.isEmpty()) {
            return new ReviewSummaryResponse(
                    movie.getMovieId(),
                    movieTitle,
                    visibleCount,
                    0,
                    averageRating,
                    false,
                    NO_REVIEWS_MESSAGE,
                    List.of());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("movie_title", FaqRagService.sanitizeContextText(movieTitle, 255));
        payload.put("visible_review_count", visibleCount);
        payload.put("average_rating", averageRating);
        payload.put("reviews", reviewItems);

        String rawResponse = generator.generate(systemPrompt, mapper.writeValueAsString(payload), schema);
        return parseResponse(movie.getMovieId(), movieTitle, visibleCount, reviewItems.size(), averageRating, rawResponse);
    }

    private ReviewSummaryResponse parseResponse(Long movieId,
                                                String movieTitle,
                                                int visibleCount,
                                                int summarizedCount,
                                                double averageRating,
                                                String json) {
        try {
            if (json == null || json.isBlank() || json.length() > 16_384) {
                throw invalidResponse();
            }
            JsonNode root = mapper.readTree(json);
            requireExactKeys(root, "summary", "highlights");

            JsonNode summaryNode = root.get("summary");
            if (summaryNode == null || !summaryNode.isString()) {
                throw invalidResponse();
            }
            String summary = validateOutputText(summaryNode.asString(), 1500);

            JsonNode highlightsNode = root.get("highlights");
            if (highlightsNode == null || !highlightsNode.isArray()
                    || highlightsNode.isEmpty() || highlightsNode.size() > 5) {
                throw invalidResponse();
            }

            List<String> highlights = new ArrayList<>(highlightsNode.size());
            for (JsonNode itemNode : highlightsNode) {
                if (itemNode == null || !itemNode.isString()) {
                    throw invalidResponse();
                }
                highlights.add(validateOutputText(itemNode.asString(), 250));
            }

            return new ReviewSummaryResponse(
                    movieId,
                    movieTitle,
                    visibleCount,
                    summarizedCount,
                    averageRating,
                    true,
                    summary,
                    List.copyOf(highlights));
        } catch (AiExtractionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalidResponse();
        }
    }

    private String validateOutputText(String text, int maxLength) {
        if (text == null || text.isBlank() || text.length() > maxLength) {
            throw invalidResponse();
        }
        try {
            return outputGuard.validate(text);
        } catch (AiExtractionException e) {
            throw invalidResponse();
        }
    }

    private static void requireExactKeys(JsonNode node, String... expected) {
        if (node == null || !node.isObject()) {
            throw invalidResponse();
        }
        Set<String> actual = new LinkedHashSet<>();
        node.propertyNames().forEach(actual::add);
        if (!actual.equals(Set.of(expected))) {
            throw invalidResponse();
        }
    }

    private static String readResource(String path) throws IOException {
        try (var stream = new ClassPathResource(path).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static AiExtractionException invalidResponse() {
        return new AiExtractionException(AiExtractionException.Kind.INVALID_RESPONSE);
    }
}

