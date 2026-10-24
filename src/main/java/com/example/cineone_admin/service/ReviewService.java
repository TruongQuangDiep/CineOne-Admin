package com.example.cineone_admin.service;

import com.example.cineone_admin.entity.Review;
import com.example.cineone_admin.repository.ReviewRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {
    private final ReviewRepository reviews;

    public Page<ReviewRow> search(String q, Byte rating, String status, int page) {
        String search = q == null ? "" : q.strip();
        return reviews.findAll((root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("user", JoinType.LEFT);
                root.fetch("movie", JoinType.LEFT);
            }
            List<Predicate> filters = new ArrayList<>();
            if (rating != null) {
                filters.add(cb.equal(root.get("rating"), rating));
            }
            if (status != null && !status.isBlank()) {
                filters.add(cb.equal(root.get("status"), status));
            }
            if (!search.isEmpty()) {
                String term = like(search);
                filters.add(cb.or(
                        cb.like(cb.lower(root.get("content")), term, '\\'),
                        cb.like(cb.lower(root.get("user").get("fullName")), term, '\\'),
                        cb.like(cb.lower(root.get("movie").get("title")), term, '\\')
                ));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        }, PageRequest.of(Math.max(0, page), 10, Sort.by(Sort.Direction.DESC, "reviewId")))
                .map(ReviewService::toRow);
    }

    public Review get(Long id) {
        return reviews.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài đánh giá"));
    }

    @Transactional
    public void toggleStatus(Long id, boolean visible) {
        get(id).setStatus(visible ? "VISIBLE" : "HIDDEN");
    }

    private static ReviewRow toRow(Review r) {
        var u = r.getUser();
        var m = r.getMovie();
        return new ReviewRow(
                r.getReviewId(),
                u != null ? u.getUserId() : null,
                u != null ? u.getFullName() : "Ẩn danh",
                u != null ? u.getEmail() : null,
                m != null ? m.getMovieId() : null,
                m != null ? m.getTitle() : "Phim không xác định",
                r.getRating(),
                r.getContent(),
                r.getStatus(),
                r.getCreatedAt()
        );
    }

    private static String like(String value) {
        String text = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    public record ReviewRow(Long reviewId, Long userId, String userName, String userEmail,
                            Long movieId, String movieTitle, Byte rating, String content,
                            String status, LocalDateTime createdAt) {}
}