package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.ai.FaqAnswerResponse;
import com.example.cineone_admin.entity.KnowledgeBaseEntry;
import com.example.cineone_admin.exception.AiExtractionException;
import com.example.cineone_admin.repository.KnowledgeBaseEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FaqRagServiceTests {

    private static KnowledgeBaseEntry entry(long id, String category, String title, String content, String status) {
        KnowledgeBaseEntry e = new KnowledgeBaseEntry();
        e.setKnowledgeId(id);
        e.setCategory(category);
        e.setTitle(title);
        e.setContent(content);
        e.setStatus(status);
        return e;
    }

    @Test
    void returnsFallbackWithoutCallingGeneratorWhenKnowledgeBaseIsEmpty() {
        KnowledgeBaseEntryRepository repo = mock(KnowledgeBaseEntryRepository.class);
        when(repo.findByStatusOrderByKnowledgeIdAsc("ACTIVE")).thenReturn(List.of());

        AtomicInteger generatorCalls = new AtomicInteger();
        CriteriaGenerator generator = (sys, user, schema) -> {
            generatorCalls.incrementAndGet();
            return "{}";
        };

        FaqRagService service = new FaqRagService(generator, repo);
        FaqAnswerResponse response = service.answer("Làm sao để đổi vé đã đặt?");

        assertFalse(response.answered());
        assertEquals(FaqRagService.NO_KNOWLEDGE_MESSAGE, response.answer());
        assertTrue(response.sources().isEmpty());
        assertEquals(0, generatorCalls.get());
    }

    @Test
    void selectsRelevantActiveEntriesAndVerifiesGroundedSources() {
        KnowledgeBaseEntry e1 = entry(10L, "CHÍNH SÁCH", "Chính sách hoàn hủy vé",
                "Vé đã thanh toán chỉ được hỗ trợ hoàn tiền khi suất chiếu bị hủy bởi rạp.", "ACTIVE");
        KnowledgeBaseEntry e2 = entry(20L, "HƯỚNG DẪN", "Hướng dẫn tích điểm thành viên",
                "Mỗi giao dịch đặt vé thành công sẽ được cộng điểm vào hạng thành viên.", "ACTIVE");

        KnowledgeBaseEntryRepository repo = mock(KnowledgeBaseEntryRepository.class);
        when(repo.findByStatusOrderByKnowledgeIdAsc("ACTIVE")).thenReturn(List.of(e1, e2));

        CriteriaGenerator generator = (system, user, schema) -> {
            var root = new JsonMapper().readTree(user);
            assertEquals("Chính sách hoàn tiền vé như thế nào?", root.get("question").asString());
            assertTrue(root.get("knowledge_entries").isArray());
            assertEquals(2, root.get("knowledge_entries").size());
            // e1 khớp nhiều từ khóa "chinh", "sach", "hoan" -> xếp hạng đầu tiên
            assertEquals(10L, root.get("knowledge_entries").get(0).get("knowledge_id").asLong());
            assertEquals(false, schema.get("additionalProperties"));
            return """
                    {
                      "answered": true,
                      "answer": "Theo chính sách của CineOne, vé đã thanh toán chỉ được hoàn tiền khi suất chiếu bị hủy bởi phía rạp.",
                      "source_ids": [10]
                    }
                    """;
        };

        FaqRagService service = new FaqRagService(generator, repo);
        FaqAnswerResponse response = service.answer("Chính sách hoàn tiền vé như thế nào?");

        assertTrue(response.answered());
        assertTrue(response.answer().contains("hoàn tiền"));
        assertEquals(1, response.sources().size());
        assertEquals(10L, response.sources().get(0).knowledgeId());
        assertEquals("Chính sách hoàn hủy vé", response.sources().get(0).title());
        assertEquals("CHÍNH SÁCH", response.sources().get(0).category());
    }

    @Test
    void filtersByCategoryCaseInsensitivelyWhenProvided() {
        KnowledgeBaseEntry e1 = entry(15L, "FAQ", "Quên mật khẩu tài khoản",
                "Chọn chức năng Quên mật khẩu tại màn hình đăng nhập để nhận mã xác thực.", "ACTIVE");

        KnowledgeBaseEntryRepository repo = mock(KnowledgeBaseEntryRepository.class);
        when(repo.findByCategoryIgnoreCaseAndStatusOrderByKnowledgeIdAsc("faq", "ACTIVE"))
                .thenReturn(List.of(e1));

        CriteriaGenerator generator = (system, user, schema) -> """
                {
                  "answered": true,
                  "answer": "Bạn hãy chọn chức năng Quên mật khẩu ở màn hình đăng nhập để lấy lại mật khẩu.",
                  "source_ids": [15]
                }
                """;

        FaqRagService service = new FaqRagService(generator, repo);
        FaqAnswerResponse response = service.answer("Quên mật khẩu thì làm sao lấy lại?", " faq ");

        assertTrue(response.answered());
        assertEquals(1, response.sources().size());
        assertEquals(15L, response.sources().get(0).knowledgeId());
        verify(repo).findByCategoryIgnoreCaseAndStatusOrderByKnowledgeIdAsc("faq", "ACTIVE");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Quên mật khẩu thì làm sao lấy lại?",
            "Tại sao tôi không nhận được mã OTP qua email?",
            "Làm sao để đổi số điện thoại đăng ký tài khoản?",
            "Thanh toán vé bằng ví điện tử có mất phí không?"
    })
    void allowsLegitimatePolicyQuestionsAboutPasswordOtpEmailOrPhone(String question) {
        FaqRagService service = new FaqRagService((s, u, sc) -> "{}", mock(KnowledgeBaseEntryRepository.class));
        String normalized = service.validateQuestion(question);
        assertFalse(normalized.isBlank());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Cho tôi hỏi vé gửi về abc@example.com chưa?",
            "Số điện thoại của tôi là 0901234567 chưa nhận được vé",
            "Mã OTP 123456 của tôi bị lỗi",
            "Mật khẩu của tôi là Secret12345 không đăng nhập được",
            "Rạp nào gần nhà tôi ở 12 Nguyễn Trãi",
            "Tọa độ 10.762622, 106.660172 gần rạp nào",
            "Token eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abc123def456"
    })
    void blocksQuestionsContainingActualPersonalDataBeforeExternalCall(String question) {
        KnowledgeBaseEntryRepository repo = mock(KnowledgeBaseEntryRepository.class);
        AtomicInteger calls = new AtomicInteger();
        FaqRagService service = new FaqRagService((s, u, sc) -> {
            calls.incrementAndGet();
            return "{}";
        }, repo);

        AiExtractionException ex = assertThrows(AiExtractionException.class,
                () -> service.answer(question));
        assertEquals(AiExtractionException.Kind.PRIVACY_REJECTED, ex.getKind());
        assertEquals(0, calls.get());
        verifyNoInteractions(repo);
    }

    @Test
    void rejectsBlankTooLongOrControlCharQuestions() {
        FaqRagService service = new FaqRagService((s, u, sc) -> "{}", mock(KnowledgeBaseEntryRepository.class));
        assertEquals(AiExtractionException.Kind.INVALID_INPUT,
                assertThrows(AiExtractionException.class, () -> service.validateQuestion(null)).getKind());
        assertEquals(AiExtractionException.Kind.INVALID_INPUT,
                assertThrows(AiExtractionException.class, () -> service.validateQuestion("   ")).getKind());
        assertEquals(AiExtractionException.Kind.INVALID_INPUT,
                assertThrows(AiExtractionException.class, () -> service.validateQuestion("a".repeat(1001))).getKind());
        assertEquals(AiExtractionException.Kind.INVALID_INPUT,
                assertThrows(AiExtractionException.class, () -> service.validateQuestion("hỏi\u0007")).getKind());
    }

    @Test
    void rejectsHallucinatedSourceIdsNotPresentInAllowedContext() {
        KnowledgeBaseEntry e1 = entry(10L, "FAQ", "Đặt vé", "Hướng dẫn đặt vé.", "ACTIVE");
        KnowledgeBaseEntryRepository repo = mock(KnowledgeBaseEntryRepository.class);
        when(repo.findByStatusOrderByKnowledgeIdAsc("ACTIVE")).thenReturn(List.of(e1));

        FaqRagService service = new FaqRagService((s, u, sc) -> """
                {
                  "answered": true,
                  "answer": "Câu trả lời có ID nguồn bịa đặt.",
                  "source_ids": [999]
                }
                """, repo);

        AiExtractionException ex = assertThrows(AiExtractionException.class,
                () -> service.answer("Cách đặt vé?"));
        assertEquals(AiExtractionException.Kind.INVALID_RESPONSE, ex.getKind());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "```json\n{\"answered\":true,\"answer\":\"ok\",\"source_ids\":[10]}\n```",
            "{\"answered\":true,\"answer\":\"ok\",\"source_ids\":[]}",
            "{\"answered\":false,\"answer\":\"Không tìm thấy\",\"source_ids\":[10]}",
            "{\"answered\":true,\"answer\":\"\",\"source_ids\":[10]}",
            "{\"answered\":true,\"answer\":\"ok\",\"source_ids\":[10],\"extra\":1}"
    })
    void rejectsMalformedOrInconsistentModelOutput(String badOutput) {
        KnowledgeBaseEntry e1 = entry(10L, "FAQ", "Đặt vé", "Hướng dẫn đặt vé.", "ACTIVE");
        KnowledgeBaseEntryRepository repo = mock(KnowledgeBaseEntryRepository.class);
        when(repo.findByStatusOrderByKnowledgeIdAsc("ACTIVE")).thenReturn(List.of(e1));

        FaqRagService service = new FaqRagService((s, u, sc) -> badOutput, repo);
        AiExtractionException ex = assertThrows(AiExtractionException.class,
                () -> service.answer("Cách đặt vé?"));
        assertEquals(AiExtractionException.Kind.INVALID_RESPONSE, ex.getKind());
    }

    @Test
    void sanitizesAccidentalPiiInKnowledgeBaseContentBeforeSendingToGemini() {
        KnowledgeBaseEntry e1 = entry(10L, "SUPPORT", "Liên hệ hỗ trợ",
                "Gửi phản hồi tại mục Hỗ trợ (không gửi qua ca.nhan@example.com hoặc 0909123456).", "ACTIVE");
        KnowledgeBaseEntryRepository repo = mock(KnowledgeBaseEntryRepository.class);
        when(repo.findByStatusOrderByKnowledgeIdAsc("ACTIVE")).thenReturn(List.of(e1));

        FaqRagService service = new FaqRagService((sys, user, sc) -> {
            assertFalse(user.contains("ca.nhan@example.com"));
            assertFalse(user.contains("0909123456"));
            return """
                    {
                      "answered": false,
                      "answer": "Xin lỗi, hiện chưa có thông tin chính thức.",
                      "source_ids": []
                    }
                    """;
        }, repo);

        FaqAnswerResponse res = service.answer("Liên hệ ở đâu?");
        assertFalse(res.answered());
        assertTrue(res.sources().isEmpty());
    }
}
