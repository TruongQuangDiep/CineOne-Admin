package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.ai.ReviewSummaryResponse;
import com.example.cineone_admin.entity.Movie;
import com.example.cineone_admin.entity.Review;
import com.example.cineone_admin.entity.User;
import com.example.cineone_admin.exception.AiExtractionException;
import com.example.cineone_admin.exception.ResourceNotFoundException;
import com.example.cineone_admin.repository.MovieRepository;
import com.example.cineone_admin.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewSummarizerServiceTests {

    private static Movie movie(long id, String title) {
        Movie m = new Movie();
        m.setMovieId(id);
        m.setTitle(title);
        return m;
    }

    private static Review review(long id, Movie movie, int rating, String content, String status) {
        User u = new User();
        u.setUserId(99L);
        u.setFullName("Nguyễn Văn Bí Mật");
        u.setEmail("bimat@cineone.vn");
        u.setPhone("0912345678");

        Review r = new Review();
        r.setReviewId(id);
        r.setMovie(movie);
        r.setUser(u);
        r.setRating((byte) rating);
        r.setContent(content);
        r.setStatus(status);
        return r;
    }

    @Test
    void rejectsInvalidMovieId() {
        MovieRepository movieRepo = mock(MovieRepository.class);
        ReviewRepository reviewRepo = mock(ReviewRepository.class);
        ReviewSummarizerService service = new ReviewSummarizerService((s, u, sc) -> "{}", movieRepo, reviewRepo);

        assertEquals(AiExtractionException.Kind.INVALID_INPUT,
                assertThrows(AiExtractionException.class, () -> service.summarize(null)).getKind());
        assertEquals(AiExtractionException.Kind.INVALID_INPUT,
                assertThrows(AiExtractionException.class, () -> service.summarize(0L)).getKind());
        assertEquals(AiExtractionException.Kind.INVALID_INPUT,
                assertThrows(AiExtractionException.class, () -> service.summarize(-5L)).getKind());
        verifyNoInteractions(movieRepo, reviewRepo);
    }

    @Test
    void throwsResourceNotFoundWhenMovieDoesNotExist() {
        MovieRepository movieRepo = mock(MovieRepository.class);
        ReviewRepository reviewRepo = mock(ReviewRepository.class);
        when(movieRepo.findById(404L)).thenReturn(Optional.empty());

        ReviewSummarizerService service = new ReviewSummarizerService((s, u, sc) -> "{}", movieRepo, reviewRepo);

        assertThrows(ResourceNotFoundException.class, () -> service.summarize(404L));
        verifyNoInteractions(reviewRepo);
    }

    @Test
    void returnsFallbackWithoutCallingGeneratorWhenMovieHasNoVisibleReviews() {
        Movie m = movie(1L, "Lật Mặt 8");
        MovieRepository movieRepo = mock(MovieRepository.class);
        ReviewRepository reviewRepo = mock(ReviewRepository.class);
        when(movieRepo.findById(1L)).thenReturn(Optional.of(m));
        when(reviewRepo.findByMovie_MovieIdAndStatusOrderByCreatedAtDescReviewIdDesc(1L, "VISIBLE"))
                .thenReturn(List.of());

        AtomicInteger calls = new AtomicInteger();
        ReviewSummarizerService service = new ReviewSummarizerService((s, u, sc) -> {
            calls.incrementAndGet();
            return "{}";
        }, movieRepo, reviewRepo);

        ReviewSummaryResponse res = service.summarize(1L);
        assertEquals(1L, res.movieId());
        assertEquals("Lật Mặt 8", res.movieTitle());
        assertEquals(0, res.reviewCount());
        assertEquals(0, res.summarizedReviewCount());
        assertEquals(0.0, res.averageRating());
        assertFalse(res.summarized());
        assertEquals(ReviewSummarizerService.NO_REVIEWS_MESSAGE, res.summary());
        assertTrue(res.highlights().isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void returnsFallbackWithoutCallingGeneratorWhenVisibleReviewsHaveNoText() {
        Movie m = movie(2L, "Mai");
        Review r1 = review(101L, m, 5, "   ", "VISIBLE");
        Review r2 = review(102L, m, 4, null, "VISIBLE");

        MovieRepository movieRepo = mock(MovieRepository.class);
        ReviewRepository reviewRepo = mock(ReviewRepository.class);
        when(movieRepo.findById(2L)).thenReturn(Optional.of(m));
        when(reviewRepo.findByMovie_MovieIdAndStatusOrderByCreatedAtDescReviewIdDesc(2L, "VISIBLE"))
                .thenReturn(List.of(r1, r2));

        AtomicInteger calls = new AtomicInteger();
        ReviewSummarizerService service = new ReviewSummarizerService((s, u, sc) -> {
            calls.incrementAndGet();
            return "{}";
        }, movieRepo, reviewRepo);

        ReviewSummaryResponse res = service.summarize(2L);
        assertEquals(2L, res.movieId());
        assertEquals(2, res.reviewCount());
        assertEquals(0, res.summarizedReviewCount());
        assertEquals(4.5, res.averageRating());
        assertFalse(res.summarized());
        assertEquals(ReviewSummarizerService.NO_REVIEWS_MESSAGE, res.summary());
        assertTrue(res.highlights().isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void summarizesVisibleReviewsWithoutLeakingUserIdentityOrEmbeddedPii() {
        Movie m = movie(5L, "Dune: Hành Tinh Cát 2");
        Review r1 = review(201L, m, 5,
                "Hình ảnh IMAX cực kỳ hoành tráng, âm thanh Hans Zimmer nổi da gà! Liên hệ tôi 0912345678 hoặc test@gmail.com",
                "VISIBLE");
        Review r2 = review(202L, m, 4,
                "Kịch bản chặt chẽ nhưng nhịp phim đoạn đầu hơi chậm.",
                "VISIBLE");

        MovieRepository movieRepo = mock(MovieRepository.class);
        ReviewRepository reviewRepo = mock(ReviewRepository.class);
        when(movieRepo.findById(5L)).thenReturn(Optional.of(m));
        when(reviewRepo.findByMovie_MovieIdAndStatusOrderByCreatedAtDescReviewIdDesc(5L, "VISIBLE"))
                .thenReturn(List.of(r1, r2));

        CriteriaGenerator generator = (system, user, schema) -> {
            // Đảm bảo thông tin người dùng và PII trong nội dung review không bao giờ lọt ra ngoài
            assertFalse(user.contains("Nguyễn Văn Bí Mật"));
            assertFalse(user.contains("bimat@cineone.vn"));
            assertFalse(user.contains("0912345678"));
            assertFalse(user.contains("test@gmail.com"));

            var root = new JsonMapper().readTree(user);
            assertEquals("Dune: Hành Tinh Cát 2", root.get("movie_title").asString());
            assertEquals(2, root.get("visible_review_count").asInt());
            assertEquals(4.5, root.get("average_rating").asDouble());
            assertEquals(2, root.get("reviews").size());

            return """
                    {
                      "summary": "Đa số khán giả đánh giá rất cao phần hình ảnh IMAX mãn nhãn và âm nhạc xuất sắc, dù một số ý kiến nhận xét nhịp phim đoạn đầu hơi chậm.",
                      "highlights": [
                        "Hình ảnh IMAX và kỹ xảo hoành tráng",
                        "Âm thanh sống động, giàu cảm xúc",
                        "Nhịp phim phần mở đầu hơi chậm"
                      ]
                    }
                    """;
        };

        ReviewSummarizerService service = new ReviewSummarizerService(generator, movieRepo, reviewRepo);
        ReviewSummaryResponse res = service.summarize(5L);

        assertEquals(5L, res.movieId());
        assertEquals("Dune: Hành Tinh Cát 2", res.movieTitle());
        assertEquals(2, res.reviewCount());
        assertEquals(2, res.summarizedReviewCount());
        assertEquals(4.5, res.averageRating());
        assertTrue(res.summarized());
        assertTrue(res.summary().contains("IMAX"));
        assertEquals(3, res.highlights().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "```json\n{\"summary\":\"ok\",\"highlights\":[\"a\"]}\n```",
            "{\"summary\":\"ok\",\"highlights\":[]}",
            "{\"summary\":\"\",\"highlights\":[\"Điểm nổi bật\"]}",
            "{\"summary\":\"ok\",\"highlights\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]}",
            "{\"summary\":\"ok\",\"highlights\":[\"a\"],\"extra\":true}"
    })
    void rejectsMalformedOrInconsistentSummaryOutput(String badJson) {
        Movie m = movie(5L, "Dune 2");
        Review r1 = review(201L, m, 5, "Phim rất hay và cuốn hút.", "VISIBLE");

        MovieRepository movieRepo = mock(MovieRepository.class);
        ReviewRepository reviewRepo = mock(ReviewRepository.class);
        when(movieRepo.findById(5L)).thenReturn(Optional.of(m));
        when(reviewRepo.findByMovie_MovieIdAndStatusOrderByCreatedAtDescReviewIdDesc(5L, "VISIBLE"))
                .thenReturn(List.of(r1));

        ReviewSummarizerService service = new ReviewSummarizerService((s, u, sc) -> badJson, movieRepo, reviewRepo);
        AiExtractionException ex = assertThrows(AiExtractionException.class,
                () -> service.summarize(5L));
        assertEquals(AiExtractionException.Kind.INVALID_RESPONSE, ex.getKind());
    }
}
