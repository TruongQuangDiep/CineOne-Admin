package com.example.cineone_admin.controller;

import com.example.cineone_admin.entity.*;
import com.example.cineone_admin.exception.AiExtractionException;
import com.example.cineone_admin.repository.*;
import com.example.cineone_admin.service.CriteriaGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:scrum20_ai;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Transactional
@Import(AiApiControllerIntegrationTests.FakeAiConfig.class)
class AiApiControllerIntegrationTests {

    @TestConfiguration
    static class FakeAiConfig {
        final AtomicReference<CriteriaGenerator> delegate = new AtomicReference<>((sys, user, sc) -> "{}");

        @Bean
        @Primary
        CriteriaGenerator testCriteriaGenerator() {
            return (systemInstruction, userContent, jsonSchema) ->
                    delegate.get().generate(systemInstruction, userContent, jsonSchema);
        }
    }

    @Autowired private WebApplicationContext context;
    @Autowired private FakeAiConfig fakeAiConfig;
    @Autowired private KnowledgeBaseEntryRepository kbRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipTierRepository tierRepository;

    private MockMvc mvc;
    private User activeUser;
    private Movie sampleMovie;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        MembershipTier tier = new MembershipTier();
        tier.setName("STANDARD");
        tier.setMinSpending(BigDecimal.ZERO);
        tier.setStatus("ACTIVE");
        tier = tierRepository.saveAndFlush(tier);

        activeUser = new User();
        activeUser.setEmail("viewer@cineone.vn");
        activeUser.setFullName("Trần Thị Khán Giả");
        activeUser.setPasswordHash("hashed_pwd");
        activeUser.setPhone("0988777666");
        activeUser.setRole("USER");
        activeUser.setStatus("ACTIVE");
        activeUser.setMembershipTier(tier);
        activeUser = userRepository.saveAndFlush(activeUser);

        sampleMovie = new Movie();
        sampleMovie.setTitle("Thám Tử Kiên");
        sampleMovie.setDuration(130);
        sampleMovie = movieRepository.saveAndFlush(sampleMovie);
    }

    // === SUBTASK SCRUM-78: RAG FAQ ENDPOINT TESTS ===

    @Test
    void anonymousUserCanAskFaqAndReceiveGroundedAnswerOnlyFromActiveEntries() throws Exception {
        KnowledgeBaseEntry activeEntry = new KnowledgeBaseEntry();
        activeEntry.setCategory("CHÍNH SÁCH");
        activeEntry.setTitle("Chính sách đổi trả vé");
        activeEntry.setContent("Vé đã mua không hỗ trợ hoàn tiền trừ khi rạp hủy suất chiếu.");
        activeEntry.setStatus("ACTIVE");
        activeEntry = kbRepository.saveAndFlush(activeEntry);

        KnowledgeBaseEntry inactiveEntry = new KnowledgeBaseEntry();
        inactiveEntry.setCategory("CHÍNH SÁCH");
        inactiveEntry.setTitle("Chính sách nội bộ cũ");
        inactiveEntry.setContent("Hoàn tiền 100% mọi trường hợp.");
        inactiveEntry.setStatus("INACTIVE");
        kbRepository.saveAndFlush(inactiveEntry);

        long activeId = activeEntry.getKnowledgeId();
        fakeAiConfig.delegate.set((system, user, schema) -> {
            assertFalse(user.contains("Chính sách nội bộ cũ"));
            var tree = new JsonMapper().readTree(user);
            assertEquals(1, tree.get("knowledge_entries").size());
            assertEquals(activeId, tree.get("knowledge_entries").get(0).get("knowledge_id").asLong());
            return """
                    {
                      "answered": true,
                      "answer": "Vé đã mua không hỗ trợ hoàn trả trừ trường hợp suất chiếu bị hủy bởi rạp.",
                      "source_ids": [%d]
                    }
                    """.formatted(activeId);
        });

        mvc.perform(post("/api/v1/ai/faq")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "Vé mua rồi có được hoàn tiền không?"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.answered").value(true))
                .andExpect(jsonPath("$.data.answer", containsString("không hỗ trợ hoàn trả")))
                .andExpect(jsonPath("$.data.sources", hasSize(1)))
                .andExpect(jsonPath("$.data.sources[0].knowledge_id").value(activeId))
                .andExpect(jsonPath("$.data.sources[0].title").value("Chính sách đổi trả vé"));
    }

    @Test
    void faqEndpointRejectsBlankOrPrivateDataQuestionsWithBadRequest() throws Exception {
        mvc.perform(post("/api/v1/ai/faq")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(post("/api/v1/ai/faq")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "Kiểm tra giúp email khachhang@gmail.com sao chưa có vé?"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("thông tin cá nhân")));
    }

    // === SUBTASK SCRUM-79: REVIEW SUMMARIZER ENDPOINT TESTS ===

    @Test
    void anonymousUserCanGetMovieReviewSummaryIgnoringHiddenReviews() throws Exception {
        Review visible1 = new Review();
        visible1.setMovie(sampleMovie);
        visible1.setUser(activeUser);
        visible1.setRating((byte) 5);
        visible1.setContent("Bối cảnh cổ trang tuyệt đẹp, diễn xuất ấn tượng.");
        visible1.setStatus("VISIBLE");
        reviewRepository.saveAndFlush(visible1);

        Review visible2 = new Review();
        visible2.setMovie(sampleMovie);
        visible2.setUser(activeUser);
        visible2.setRating((byte) 4);
        visible2.setContent("Vụ án hấp dẫn, âm nhạc rùng rợn hợp không khí.");
        visible2.setStatus("VISIBLE");
        reviewRepository.saveAndFlush(visible2);

        Review hiddenReview = new Review();
        hiddenReview.setMovie(sampleMovie);
        hiddenReview.setUser(activeUser);
        hiddenReview.setRating((byte) 1);
        hiddenReview.setContent("Bình luận spam quảng cáo đã bị ẩn.");
        hiddenReview.setStatus("HIDDEN");
        reviewRepository.saveAndFlush(hiddenReview);

        fakeAiConfig.delegate.set((system, user, schema) -> {
            assertFalse(user.contains("Bình luận spam quảng cáo đã bị ẩn"));
            assertFalse(user.contains("Trần Thị Khán Giả"));
            assertFalse(user.contains("viewer@cineone.vn"));
            return """
                    {
                      "summary": "Khán giả khen ngợi bối cảnh cổ trang đẹp mắt, diễn xuất tốt và cốt truyện trinh thám cuốn hút.",
                      "highlights": [
                        "Bối cảnh cổ trang được đầu tư chỉn chu",
                        "Cốt truyện phá án hấp dẫn, âm nhạc hợp không khí"
                      ]
                    }
                    """;
        });

        mvc.perform(get("/api/v1/movies/{movieId}/reviews/summary", sampleMovie.getMovieId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.movie_id").value(sampleMovie.getMovieId()))
                .andExpect(jsonPath("$.data.movie_title").value("Thám Tử Kiên"))
                .andExpect(jsonPath("$.data.review_count").value(2))
                .andExpect(jsonPath("$.data.summarized_review_count").value(2))
                .andExpect(jsonPath("$.data.average_rating").value(4.5))
                .andExpect(jsonPath("$.data.summarized").value(true))
                .andExpect(jsonPath("$.data.highlights", hasSize(2)));

        // Kiểm tra đường dẫn alias dưới /api/v1/ai/movies/{movieId}/review-summary
        mvc.perform(get("/api/v1/ai/movies/{movieId}/review-summary", sampleMovie.getMovieId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.movie_id").value(sampleMovie.getMovieId()))
                .andExpect(jsonPath("$.data.summarized").value(true));
    }

    @Test
    void reviewSummaryReturnsNotFoundWhenMovieDoesNotExist() throws Exception {
        mvc.perform(get("/api/v1/movies/{movieId}/reviews/summary", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void mapsAiRateLimitAndTimeoutToAppropriateHttpStatusCodes() throws Exception {
        KnowledgeBaseEntry activeEntry = new KnowledgeBaseEntry();
        activeEntry.setCategory("FAQ");
        activeEntry.setTitle("Hỗ trợ đặt vé");
        activeEntry.setContent("Đặt vé qua ứng dụng CineOne.");
        activeEntry.setStatus("ACTIVE");
        kbRepository.saveAndFlush(activeEntry);

        fakeAiConfig.delegate.set((system, user, schema) -> {
            throw new AiExtractionException(AiExtractionException.Kind.RATE_LIMITED);
        });

        mvc.perform(post("/api/v1/ai/faq")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Đặt vé ở đâu?\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));

        fakeAiConfig.delegate.set((system, user, schema) -> {
            throw new AiExtractionException(AiExtractionException.Kind.TIMEOUT);
        });

        mvc.perform(post("/api/v1/ai/faq")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Đặt vé ở đâu?\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value(504));
    }
}
