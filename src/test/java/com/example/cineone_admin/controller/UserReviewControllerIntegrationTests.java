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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:userreview17;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
@Transactional
@WithMockUser(roles = "ADMIN")
class UserReviewControllerIntegrationTests {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired MembershipTierRepository tiers;
    @Autowired MovieRepository movies;
    @Autowired ReviewRepository reviews;

    MockMvc mvc;
    MembershipTier standardTier, vipTier;
    User activeUser;
    Movie sampleMovie;
    Review visibleReview;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        standardTier = new MembershipTier();
        standardTier.setName("STANDARD");
        standardTier.setMinSpending(BigDecimal.ZERO);
        standardTier.setStatus("ACTIVE");
        standardTier = tiers.saveAndFlush(standardTier);

        vipTier = new MembershipTier();
        vipTier.setName("VIP");
        vipTier.setMinSpending(new BigDecimal("10000000.00"));
        vipTier.setStatus("ACTIVE");
        vipTier = tiers.saveAndFlush(vipTier);

        activeUser = new User();
        activeUser.setEmail("diep@cineone.vn");
        activeUser.setFullName("Trương Quang Điệp");
        activeUser.setPasswordHash("encoded_pwd");
        activeUser.setPhone("0912345678");
        activeUser.setDateOfBirth(LocalDate.of(2005, 5, 15));
        activeUser.setRole("USER");
        activeUser.setMembershipTier(standardTier);
        activeUser.setStatus("ACTIVE");
        activeUser = users.saveAndFlush(activeUser);

        sampleMovie = new Movie();
        sampleMovie.setTitle("Natra 2: Ma Đồng Giáng Thế");
        sampleMovie.setDuration(146);
        sampleMovie = movies.saveAndFlush(sampleMovie);

        visibleReview = new Review();
        visibleReview.setUser(activeUser);
        visibleReview.setMovie(sampleMovie);
        visibleReview.setRating((byte) 5);
        visibleReview.setContent("Phim xuất sắc, hiệu ứng hình ảnh rất mãn nhãn!");
        visibleReview.setStatus("VISIBLE");
        visibleReview = reviews.saveAndFlush(visibleReview);
    }

    // --- SUBTASK SCRUM-70: QUẢN TRỊ NGƯỜI DÙNG & HẠNG THÀNH VIÊN ---

    @Test
    void rendersAllUserScreensSuccessfully() throws Exception {
        mvc.perform(get("/users")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Trương Quang Điệp")))
                .andExpect(content().string(containsString("QUẢN LÝ NGƯỜI DÙNG")));

        mvc.perform(get("/users/{id}", activeUser.getUserId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("diep@cineone.vn")))
                .andExpect(content().string(containsString("STANDARD")));

        mvc.perform(get("/users/{id}/edit", activeUser.getUserId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("ĐIỀU CHỈNH HẠNG THÀNH VIÊN")));
    }

    @Test
    void updatesUserMembershipTierSuccessfully() throws Exception {
        // Nâng hạng người dùng lên VIP
        mvc.perform(post("/users/{id}", activeUser.getUserId()).with(csrf())
                        .param("membershipTierId", vipTier.getMembershipTierId().toString()))
                .andExpect(status().is3xxRedirection());

        var updated = users.findById(activeUser.getUserId()).orElseThrow();
        assertEquals("VIP", updated.getMembershipTier().getName());
        assertEquals("Trương Quang Điệp", updated.getFullName()); // Thông tin cá nhân giữ nguyên
    }

    @Test
    void locksAndUnlocksUserAccount() throws Exception {
        mvc.perform(post("/users/{id}/status", activeUser.getUserId())
                        .with(csrf()).param("status", "LOCKED"))
                .andExpect(status().is3xxRedirection());

        assertEquals("LOCKED", users.findById(activeUser.getUserId()).orElseThrow().getStatus());

        mvc.perform(post("/users/{id}/status", activeUser.getUserId())
                        .with(csrf()).param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection());

        assertEquals("ACTIVE", users.findById(activeUser.getUserId()).orElseThrow().getStatus());
    }

    @Test
    void managesMembershipTiers() throws Exception {
        mvc.perform(get("/memberships")).andExpect(status().isOk())
                .andExpect(content().string(containsString("VIP")))
                .andExpect(content().string(containsString("CẤU HÌNH HẠNG THÀNH VIÊN")));

        mvc.perform(get("/memberships/new")).andExpect(status().isOk())
                .andExpect(content().string(containsString("THÊM HẠNG THÀNH VIÊN")));

        mvc.perform(post("/memberships").with(csrf())
                        .param("name", "DIAMOND")
                        .param("minSpending", "25000000")
                        .param("description", "Hạng kim cương")
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection());

        assertTrue(tiers.findByName("DIAMOND").isPresent());

        // Toggle vô hiệu hóa hạng thành viên
        mvc.perform(post("/memberships/{id}/status", standardTier.getMembershipTierId())
                        .with(csrf()).param("enabled", "false"))
                .andExpect(status().is3xxRedirection());

        assertEquals("INACTIVE", tiers.findById(standardTier.getMembershipTierId()).orElseThrow().getStatus());
    }

    // --- SUBTASK SCRUM-71: MÀN HÌNH QUẢN LÝ & KIỂM DUYỆT ĐÁNH GIÁ ---

    @Test
    void filtersReviewsAndTogglesVisibility() throws Exception {
        mvc.perform(get("/reviews")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Natra 2")))
                .andExpect(content().string(containsString("Trương Quang Điệp")))
                .andExpect(content().string(containsString("5 ★")));

        // Lọc theo rating chính xác
        mvc.perform(get("/reviews").param("rating", "5")).andExpect(status().isOk())
                .andExpect(content().string(containsString("5 ★")));

        mvc.perform(get("/reviews").param("rating", "1")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Không có bài đánh giá nào phù hợp.")));

        // Ẩn bài đánh giá vi phạm
        mvc.perform(post("/reviews/{id}/status", visibleReview.getReviewId())
                        .with(csrf()).param("visible", "false"))
                .andExpect(status().is3xxRedirection());

        assertEquals("HIDDEN", reviews.findById(visibleReview.getReviewId()).orElseThrow().getStatus());

        // Duyệt lại bài đánh giá
        mvc.perform(post("/reviews/{id}/status", visibleReview.getReviewId())
                        .with(csrf()).param("visible", "true"))
                .andExpect(status().is3xxRedirection());

        assertEquals("VISIBLE", reviews.findById(visibleReview.getReviewId()).orElseThrow().getStatus());
    }

    // --- BẢO MẬT PHÂN QUYỀN VÀ BẢO VỆ CSRF ---

    @Test
    void protectsRoutesAgainstUnauthorizedAccess() throws Exception {
        // Chưa đăng nhập -> Chuyển hướng sang trang login (3xx)
        mvc.perform(get("/users").with(anonymous())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/memberships").with(anonymous())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/reviews").with(anonymous())).andExpect(status().is3xxRedirection());

        // Đăng nhập quyền USER thông thường -> Bị cấm truy cập (403 Forbidden)
        mvc.perform(get("/users").with(user("client").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/memberships").with(user("client").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/reviews").with(user("client").roles("USER"))).andExpect(status().isForbidden());

        // POST thao tác thiếu CSRF token -> 403 Forbidden
        mvc.perform(post("/users/{id}/status", activeUser.getUserId()).param("status", "LOCKED"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/reviews/{id}/status", visibleReview.getReviewId()).param("visible", "false"))
                .andExpect(status().isForbidden());
    }
}