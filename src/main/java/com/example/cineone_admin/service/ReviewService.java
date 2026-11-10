package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.review.*;
import com.example.cineone_admin.entity.Movie;
import com.example.cineone_admin.entity.Review;
import com.example.cineone_admin.entity.Ticket;
import com.example.cineone_admin.entity.User;
import com.example.cineone_admin.repository.MovieRepository;
import com.example.cineone_admin.repository.ReviewRepository;
import com.example.cineone_admin.repository.TicketRepository;
import com.example.cineone_admin.repository.UserRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {
    private final ReviewRepository reviews;
    private final MovieRepository movies;
    private final TicketRepository tickets;
    private final UserRepository users;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ReviewSummarizerService reviewSummarizerService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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

    public MovieReviewsResponse getMovieReviews(Long movieId) {
        Movie movie = movies.findById(movieId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bộ phim với mã " + movieId));

        List<Review> reviewList = reviews.findByMovie_MovieIdAndStatusOrderByCreatedAtDescReviewIdDesc(movieId, "VISIBLE");
        int count = reviewList.size();
        double avg = 0.0;
        if (count > 0) {
            double sum = reviewList.stream().mapToInt(r -> r.getRating() != null ? r.getRating().intValue() : 0).sum();
            avg = Math.round((sum / count) * 10.0) / 10.0;
        }

        List<MovieReviewItemResponse> items = reviewList.stream().map(r -> {
            User u = r.getUser();
            String uName = u != null && u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : "Khán giả CineOne";
            String uEmail = u != null ? maskEmail(u.getEmail()) : null;
            String created = r.getCreatedAt() != null ? r.getCreatedAt().format(DATE_FORMATTER) : "";
            return MovieReviewItemResponse.builder()
                    .reviewId(r.getReviewId())
                    .userId(u != null ? u.getUserId() : null)
                    .userName(uName)
                    .userEmail(uEmail)
                    .rating(r.getRating() != null ? r.getRating().intValue() : 5)
                    .content(r.getContent() != null ? r.getContent() : "")
                    .createdAt(created)
                    .build();
        }).toList();

        return MovieReviewsResponse.builder()
                .movieId(movie.getMovieId())
                .movieTitle(movie.getTitle())
                .averageRating(avg)
                .reviewCount(count)
                .reviews(items)
                .build();
    }

    public List<MyReviewItemResponse> getMyReviews(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để xem đánh giá cá nhân.");
        }

        List<Review> list = reviews.findByUser_UserIdOrderByCreatedAtDescReviewIdDesc(user.getUserId());
        return list.stream().map(r -> {
            Movie m = r.getMovie();
            String created = r.getCreatedAt() != null ? r.getCreatedAt().format(DATE_FORMATTER) : "";
            return MyReviewItemResponse.builder()
                    .reviewId(r.getReviewId())
                    .movieId(m != null ? m.getMovieId() : null)
                    .movieTitle(m != null ? m.getTitle() : "Phim không xác định")
                    .posterUrl(m != null ? m.getPosterUrl() : null)
                    .rating(r.getRating() != null ? r.getRating().intValue() : 5)
                    .content(r.getContent() != null ? r.getContent() : "")
                    .status(r.getStatus())
                    .createdAt(created)
                    .build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public ReviewEligibilityResponse checkEligibility(Long movieId, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        if (user == null) {
            return ReviewEligibilityResponse.builder()
                    .eligible(false)
                    .alreadyReviewed(false)
                    .message("Vui lòng đăng nhập tài khoản để đánh giá phim.")
                    .build();
        }

        Optional<Review> existing = reviews.findFirstByUser_UserIdAndMovie_MovieId(user.getUserId(), movieId);
        boolean hasWatched = hasWatchedMovie(user.getUserId(), movieId);

        if (!hasWatched) {
            return ReviewEligibilityResponse.builder()
                    .eligible(false)
                    .alreadyReviewed(existing.isPresent())
                    .existingRating(existing.map(r -> r.getRating() != null ? r.getRating().intValue() : 5).orElse(null))
                    .existingContent(existing.map(Review::getContent).orElse(null))
                    .message("Bạn chỉ có thể đánh giá sau khi đã mua vé và suất chiếu đã kết thúc.")
                    .build();
        }

        return ReviewEligibilityResponse.builder()
                .eligible(true)
                .alreadyReviewed(existing.isPresent())
                .existingRating(existing.map(r -> r.getRating() != null ? r.getRating().intValue() : 5).orElse(null))
                .existingContent(existing.map(Review::getContent).orElse(null))
                .message(existing.isPresent()
                        ? "Bạn đã đánh giá phim này rồi. Bạn có thể gửi lại để cập nhật nhận xét."
                        : "Đủ điều kiện viết đánh giá.")
                .build();
    }

    @Transactional
    public MovieReviewItemResponse createOrUpdateReview(CreateReviewApiRequest request, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để đánh giá phim.");
        }
        if (request == null || request.getMovieId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu thông tin mã phim cần đánh giá.");
        }
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Điểm đánh giá phải từ 1 đến 5 sao.");
        }

        Movie movie = movies.findById(request.getMovieId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bộ phim với mã " + request.getMovieId()));

        boolean hasWatched = hasWatchedMovie(user.getUserId(), movie.getMovieId());
        if (!hasWatched) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bạn chỉ được viết đánh giá cho phim khi đã có vé điện tử hợp lệ và suất chiếu tương ứng đã kết thúc.");
        }

        Optional<Review> existingOpt = reviews.findFirstByUser_UserIdAndMovie_MovieId(user.getUserId(), movie.getMovieId());
        Review review;
        if (existingOpt.isPresent()) {
            review = existingOpt.get();
            review.setRating(request.getRating().byteValue());
            review.setContent(request.getContent() != null ? request.getContent().trim() : "");
            review.setStatus("VISIBLE");
        } else {
            review = new Review();
            review.setUser(user);
            review.setMovie(movie);
            review.setRating(request.getRating().byteValue());
            review.setContent(request.getContent() != null ? request.getContent().trim() : "");
            review.setStatus("VISIBLE");
        }

        Review saved = reviews.save(review);
        if (reviewSummarizerService != null) {
            reviewSummarizerService.invalidate(movie.getMovieId());
        }
        String created = saved.getCreatedAt() != null ? saved.getCreatedAt().format(DATE_FORMATTER) : LocalDateTime.now().format(DATE_FORMATTER);

        return MovieReviewItemResponse.builder()
                .reviewId(saved.getReviewId())
                .userId(user.getUserId())
                .userName(user.getFullName() != null ? user.getFullName() : "Khán giả CineOne")
                .userEmail(maskEmail(user.getEmail()))
                .rating(saved.getRating().intValue())
                .content(saved.getContent())
                .createdAt(created)
                .build();
    }

    private boolean hasWatchedMovie(Long userId, Long movieId) {
        if (userId == null || movieId == null) return false;
        return tickets.existsWatchedMovieTicket(userId, movieId, LocalDateTime.now());
    }

    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        String principal = authentication.getName();
        if (principal.contains("@")) {
            return users.findByEmail(principal).orElse(null);
        }
        try {
            Long userId = Long.parseLong(principal);
            return users.findById(userId).orElse(null);
        } catch (NumberFormatException e) {
            return users.findByEmail(principal).orElse(null);
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int at = email.indexOf('@');
        if (at <= 2) return "***" + email.substring(at);
        return email.substring(0, 2) + "****" + email.substring(at);
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
