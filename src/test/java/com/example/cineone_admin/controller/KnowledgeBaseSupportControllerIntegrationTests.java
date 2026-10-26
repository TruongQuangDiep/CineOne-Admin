package com.example.cineone_admin.controller;

import com.example.cineone_admin.entity.*;
import com.example.cineone_admin.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:scrum18;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@WithMockUser(roles = "ADMIN")
class KnowledgeBaseSupportControllerIntegrationTests {

    @Autowired private WebApplicationContext context;
    @Autowired private KnowledgeBaseEntryRepository kbRepository;
    @Autowired private SupportRequestRepository supportRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipTierRepository membershipTierRepository;

    private MockMvc mvc;
    private User testUser;
    private MembershipTier standardTier;

    @BeforeEach
    void setup() {
        supportRepository.deleteAll();
        kbRepository.deleteAll();
        userRepository.deleteAll();
        membershipTierRepository.deleteAll();

        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        standardTier = new MembershipTier();
        standardTier.setName("STANDARD");
        standardTier.setMinSpending(BigDecimal.ZERO);
        standardTier.setStatus("ACTIVE");
        standardTier = membershipTierRepository.saveAndFlush(standardTier);

        testUser = new User();
        testUser.setEmail("customer@cineone.vn");
        testUser.setFullName("Nguyễn Văn Khách");
        testUser.setPasswordHash("encoded_pwd");
        testUser.setRole("USER");
        testUser.setStatus("ACTIVE");
        testUser.setMembershipTier(standardTier);
        testUser = userRepository.saveAndFlush(testUser);
    }

    // === SUBTASK SCRUM-72: KNOWLEDGE BASE TESTS ===

    @Test
    void rendersKnowledgeBaseListAndFilters() throws Exception {
        KnowledgeBaseEntry entry = new KnowledgeBaseEntry();
        entry.setTitle("Cách đặt vé trực tuyến");
        entry.setCategory("FAQ");
        entry.setContent("Chọn rạp, chọn phim, chọn suất chiếu và thanh toán.");
        entry.setStatus("ACTIVE");
        kbRepository.saveAndFlush(entry);

        mvc.perform(get("/knowledgebase"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TRỢ GIÚP &amp; CHÍNH SÁCH")))
                .andExpect(content().string(containsString("Cách đặt vé trực tuyến")));

        mvc.perform(get("/knowledgebase").param("keyword", "đặt vé"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cách đặt vé trực tuyến")));
    }

    @Test
    void createsKnowledgeBaseEntrySuccessfully() throws Exception {
        mvc.perform(post("/knowledgebase/create")
                        .with(csrf())
                        .param("title", "Chính sách hoàn tiền vé")
                        .param("category", "CHÍNH SÁCH")
                        .param("content", "Vé chỉ được hoàn tiền trước giờ chiếu 120 phút.")
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/knowledgebase"));

        assertEquals(1, kbRepository.count());
        assertTrue(kbRepository.existsByTitleIgnoreCase("Chính sách hoàn tiền vé"));
    }

    @Test
    void rejectsDuplicateTitleAndInvalidCategoryOnCreate() throws Exception {
        KnowledgeBaseEntry entry = new KnowledgeBaseEntry();
        entry.setTitle("Quy định chung");
        entry.setCategory("FAQ");
        entry.setContent("Nội dung quy định.");
        entry.setStatus("ACTIVE");
        kbRepository.saveAndFlush(entry);

        // Trùng tiêu đề -> Form trả về lỗi
        mvc.perform(post("/knowledgebase/create")
                        .with(csrf())
                        .param("title", "Quy định chung")
                        .param("category", "FAQ")
                        .param("content", "Trùng lặp")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tiêu đề bài viết đã tồn tại.")));

        // Danh mục không nằm trong allowlist
        mvc.perform(post("/knowledgebase/create")
                        .with(csrf())
                        .param("title", "Bài viết mới lạ")
                        .param("category", "UNKNOWN_CAT")
                        .param("content", "Nội dung hợp lệ")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Danh mục không hợp lệ.")));
    }

    @Test
    void updatesKnowledgeBaseEntrySuccessfully() throws Exception {
        KnowledgeBaseEntry entry = new KnowledgeBaseEntry();
        entry.setTitle("Tiêu đề cũ");
        entry.setCategory("FAQ");
        entry.setContent("Nội dung cũ.");
        entry.setStatus("ACTIVE");
        entry = kbRepository.saveAndFlush(entry);

        mvc.perform(post("/knowledgebase/{id}/edit", entry.getKnowledgeId())
                        .with(csrf())
                        .param("title", "Tiêu đề đã sửa")
                        .param("category", "HƯỚNG DẪN")
                        .param("content", "Nội dung mới chi tiết hơn.")
                        .param("status", "INACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/knowledgebase"));

        KnowledgeBaseEntry updated = kbRepository.findById(entry.getKnowledgeId()).orElseThrow();
        assertEquals("Tiêu đề đã sửa", updated.getTitle());
        assertEquals("HƯỚNG DẪN", updated.getCategory());
        assertEquals("INACTIVE", updated.getStatus());
    }

    @Test
    void togglesAndDeletesKnowledgeBaseEntry() throws Exception {
        KnowledgeBaseEntry entry = new KnowledgeBaseEntry();
        entry.setTitle("Hướng dẫn đổi voucher");
        entry.setCategory("HƯỚNG DẪN");
        entry.setContent("Nhập mã voucher tại bước thanh toán.");
        entry.setStatus("ACTIVE");
        entry = kbRepository.saveAndFlush(entry);

        // Bật / tắt trạng thái
        mvc.perform(post("/knowledgebase/{id}/toggle-status", entry.getKnowledgeId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/knowledgebase"));
        assertEquals("INACTIVE", kbRepository.findById(entry.getKnowledgeId()).orElseThrow().getStatus());

        // Xóa bản ghi
        mvc.perform(post("/knowledgebase/{id}/delete", entry.getKnowledgeId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/knowledgebase"));
        assertEquals(0, kbRepository.count());
    }

    // === SUBTASK SCRUM-73: SUPPORT REQUEST TESTS ===

    @Test
    void rendersSupportRequestListAndFilters() throws Exception {
        SupportRequest sr = new SupportRequest();
        sr.setUser(testUser);
        sr.setType("KHIẾU NẠI");
        sr.setCategory("THANH TOÁN");
        sr.setContent("Tôi bị trừ tiền nhưng không nhận được vé.");
        sr.setStatus("PENDING");
        supportRepository.saveAndFlush(sr);

        mvc.perform(get("/support"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("YÊU CẦU HỖ TRỢ &amp; KHIẾU NẠI")))
                .andExpect(content().string(containsString("Tôi bị trừ tiền")));

        mvc.perform(get("/support").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("customer@cineone.vn")));
    }

    @Test
    void rendersSupportDetailAndSubmitsResponse() throws Exception {
        SupportRequest sr = new SupportRequest();
        sr.setUser(testUser);
        sr.setType("HỖ TRỢ");
        sr.setCategory("VÉ");
        sr.setContent("Nhờ kiểm tra giúp mã QR của suất chiếu hôm nay.");
        sr.setStatus("PENDING");
        sr = supportRepository.saveAndFlush(sr);

        mvc.perform(get("/support/{id}", sr.getRequestId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CHI TIẾT YÊU CẦU")))
                .andExpect(content().string(containsString("Nhờ kiểm tra giúp mã QR")));

        mvc.perform(post("/support/{id}/respond", sr.getRequestId())
                        .with(csrf())
                        .param("response", "Đã kiểm tra vé của bạn hợp lệ, hãy đến quầy lấy vé.")
                        .param("status", "RESOLVED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/support/" + sr.getRequestId()));

        SupportRequest updated = supportRepository.findById(sr.getRequestId()).orElseThrow();
        assertEquals("RESOLVED", updated.getStatus());
        assertEquals("Đã kiểm tra vé của bạn hợp lệ, hãy đến quầy lấy vé.", updated.getResponse());
        assertEquals("PENDING", updated.getEmailStatus()); // Ghi nhận trạng thái hàng đợi email
        assertNotNull(updated.getRespondedAt());
    }

    @Test
    void rejectsInvalidResponseSubmission() throws Exception {
        SupportRequest sr = new SupportRequest();
        sr.setUser(testUser);
        sr.setType("HỖ TRỢ");
        sr.setCategory("TÀI KHOẢN");
        sr.setContent("Cần đổi email.");
        sr.setStatus("PENDING");
        sr = supportRepository.saveAndFlush(sr);

        // Bỏ trống response
        mvc.perform(post("/support/{id}/respond", sr.getRequestId())
                        .with(csrf())
                        .param("response", "   ")
                        .param("status", "RESOLVED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));

        // Status không hợp lệ
        mvc.perform(post("/support/{id}/respond", sr.getRequestId())
                        .with(csrf())
                        .param("response", "Hợp lệ")
                        .param("status", "INVALID_STATUS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void updatesSupportStatusDirectly() throws Exception {
        SupportRequest sr = new SupportRequest();
        sr.setUser(testUser);
        sr.setType("HỖ TRỢ");
        sr.setCategory("KHÁC");
        sr.setContent("Hỏi thông tin sự kiện.");
        sr.setStatus("PENDING");
        sr = supportRepository.saveAndFlush(sr);

        mvc.perform(post("/support/{id}/status", sr.getRequestId())
                        .with(csrf())
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/support/" + sr.getRequestId()));

        assertEquals("IN_PROGRESS", supportRepository.findById(sr.getRequestId()).orElseThrow().getStatus());
    }

    // === BẢO MẬT & CSRF ===

    @Test
    void protectsSupportAndKnowledgeBaseAgainstUnauthorizedAccess() throws Exception {
        // Chưa đăng nhập -> Redirect 3xx
        mvc.perform(get("/knowledgebase").with(anonymous())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/support").with(anonymous())).andExpect(status().is3xxRedirection());

        // Đăng nhập quyền USER thông thường -> 403 Forbidden
        mvc.perform(get("/knowledgebase").with(user("client").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/support").with(user("client").roles("USER"))).andExpect(status().isForbidden());

        // Thiếu CSRF token -> 403 Forbidden
        mvc.perform(post("/knowledgebase/create")
                        .param("title", "No CSRF")
                        .param("category", "FAQ")
                        .param("content", "Content"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/support/1/status").param("status", "RESOLVED"))
                .andExpect(status().isForbidden());
    }
}