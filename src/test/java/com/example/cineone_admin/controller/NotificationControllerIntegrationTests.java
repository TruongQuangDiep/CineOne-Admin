package com.example.cineone_admin.controller;

import com.example.cineone_admin.entity.*;
import com.example.cineone_admin.repository.*;
import com.example.cineone_admin.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:scrum49;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@WithMockUser(roles = "ADMIN")
class NotificationControllerIntegrationTests {

    @Autowired private WebApplicationContext context;
    @Autowired private NotificationCampaignRepository campaignRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipTierRepository membershipTierRepository;
    @Autowired private NotificationService notificationService;

    private MockMvc mvc;
    private User normalUser;
    private User vipUser;

    @BeforeEach
    void setup() {
        notificationRepository.deleteAll();
        campaignRepository.deleteAll();
        userRepository.deleteAll();
        membershipTierRepository.deleteAll();

        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        MembershipTier normalTier = new MembershipTier();
        normalTier.setName("NORMAL");
        normalTier.setMinSpending(BigDecimal.ZERO);
        normalTier.setStatus("ACTIVE");
        normalTier = membershipTierRepository.saveAndFlush(normalTier);

        MembershipTier vipTier = new MembershipTier();
        vipTier.setName("VIP");
        vipTier.setMinSpending(new BigDecimal("5000000"));
        vipTier.setStatus("ACTIVE");
        vipTier = membershipTierRepository.saveAndFlush(vipTier);

        normalUser = new User();
        normalUser.setEmail("normal@cineone.vn");
        normalUser.setPhone("0901112222");
        normalUser.setFullName("Nguyễn Văn Thường");
        normalUser.setPasswordHash("encoded_pwd");
        normalUser.setRole("USER");
        normalUser.setStatus("ACTIVE");
        normalUser.setMembershipTier(normalTier);
        normalUser = userRepository.saveAndFlush(normalUser);

        vipUser = new User();
        vipUser.setEmail("vip@cineone.vn");
        vipUser.setPhone("0987654321");
        vipUser.setFullName("Trương Quang Điệp");
        vipUser.setPasswordHash("encoded_pwd");
        vipUser.setRole("USER");
        vipUser.setStatus("ACTIVE");
        vipUser.setMembershipTier(vipTier);
        vipUser = userRepository.saveAndFlush(vipUser);
    }

    @Test
    void rendersCampaignListAndUserTab() throws Exception {
        NotificationCampaign c = new NotificationCampaign();
        c.setTitle("Giảm 20% cuối tuần");
        c.setMessage("Nhập mã WEEKEND giảm 20%.");
        c.setType("PROMOTION");
        c.setTargetType("ALL_USERS");
        c.setStatus("SENT");
        campaignRepository.saveAndFlush(c);

        mvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("QUẢN LÝ THÔNG BÁO")))
                .andExpect(content().string(containsString("Giảm 20% cuối tuần")));

        mvc.perform(get("/notifications").param("tab", "user"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tạo thông báo riêng cho người dùng")));
    }

    @Test
    void createsAndSendsCampaignImmediatelyToAllUsers() throws Exception {
        mvc.perform(post("/notifications/create")
                        .with(csrf())
                        .param("title", "Bảo trì hệ thống")
                        .param("message", "Hệ thống bảo trì từ 01:00 đến 03:00 sáng mai.")
                        .param("type", "SYSTEM")
                        .param("targetType", "ALL_USERS")
                        .param("action", "SEND_NOW"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notifications"));

        List<NotificationCampaign> campaigns = campaignRepository.findAll();
        assertEquals(1, campaigns.size());
        assertEquals("SENT", campaigns.get(0).getStatus());
        assertEquals(2, notificationRepository.count());
    }

    @Test
    void createsScheduledCampaignAndDispatchesToTargetTierOnly() throws Exception {
        String futureTime = LocalDateTime.now().plusHours(2).withNano(0).withSecond(0).toString();

        mvc.perform(post("/notifications/create")
                        .with(csrf())
                        .param("title", "Ưu đãi riêng cho VIP")
                        .param("message", "Tặng bắp nước miễn phí cho thành viên VIP.")
                        .param("type", "PROMOTION")
                        .param("targetType", "VIP")
                        .param("action", "SCHEDULE")
                        .param("scheduledAt", futureTime))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notifications"));

        NotificationCampaign scheduled = campaignRepository.findAll().get(0);
        assertEquals("SCHEDULED", scheduled.getStatus());
        assertEquals(0, notificationRepository.count());

        mvc.perform(post("/notifications/{id}/send", scheduled.getCampaignId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notifications"));

        NotificationCampaign sent = campaignRepository.findById(scheduled.getCampaignId()).orElseThrow();
        assertEquals("SENT", sent.getStatus());
        assertEquals(1, notificationRepository.count());
        assertEquals(vipUser.getUserId(), notificationRepository.findAll().get(0).getUser().getUserId());
    }

    @Test
    void updatesAndCancelsUnsentCampaign() throws Exception {
        NotificationCampaign draft = new NotificationCampaign();
        draft.setTitle("Bản nháp ban đầu");
        draft.setMessage("Nội dung nháp.");
        draft.setType("REMINDER");
        draft.setTargetType("NORMAL");
        draft.setStatus("DRAFT");
        draft = campaignRepository.saveAndFlush(draft);

        mvc.perform(post("/notifications/{id}/edit", draft.getCampaignId())
                        .with(csrf())
                        .param("title", "Bản nháp đã cập nhật")
                        .param("message", "Nội dung mới.")
                        .param("type", "SYSTEM")
                        .param("targetType", "ALL_USERS")
                        .param("action", "DRAFT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notifications"));

        NotificationCampaign updated = campaignRepository.findById(draft.getCampaignId()).orElseThrow();
        assertEquals("Bản nháp đã cập nhật", updated.getTitle());
        assertEquals("DRAFT", updated.getStatus());

        mvc.perform(post("/notifications/{id}/cancel", draft.getCampaignId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notifications"));

        assertEquals("CANCELLED", campaignRepository.findById(draft.getCampaignId()).orElseThrow().getStatus());
    }

    @Test
    void checksUserAndSendsIndividualNotification() throws Exception {
        mvc.perform(get("/notifications/suggest-users").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(get("/notifications/suggest-users").param("q", "quang diep"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("vip@cineone.vn"));

        mvc.perform(get("/notifications/check-user").param("q", "+84 987 654 321"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.email").value("vip@cineone.vn"))
                .andExpect(jsonPath("$.tier").value("VIP"));

        mvc.perform(post("/notifications/send-user")
                        .with(csrf())
                        .param("userId", String.valueOf(vipUser.getUserId()))
                        .param("type", "SYSTEM")
                        .param("title", "Hệ thống cần bảo trì tài khoản của bạn")
                        .param("message", "Chào bạn, CineOne đã kiểm tra sự cố giao dịch của bạn.")
                        .param("sendApp", "true")
                        .param("sendEmail", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notifications?tab=user"));

        List<Notification> userNotifs = notificationRepository.findByUser_UserIdOrderByCreatedAtDescNotificationIdDesc(vipUser.getUserId());
        assertEquals(1, userNotifs.size());
        assertEquals("Hệ thống cần bảo trì tài khoản của bạn", userNotifs.get(0).getTitle());
        assertNull(userNotifs.get(0).getCampaign());
    }

    @Test
    void processesDueScheduledCampaignsAutomatically() {
        NotificationCampaign due = new NotificationCampaign();
        due.setTitle("Thông báo tự động theo lịch");
        due.setMessage("Nội dung tự động phát khi đến giờ.");
        due.setType("REMINDER");
        due.setTargetType("NORMAL");
        due.setScheduledAt(LocalDateTime.now().minusMinutes(5));
        due.setStatus("SCHEDULED");
        due = campaignRepository.saveAndFlush(due);

        notificationService.processScheduledCampaigns();

        NotificationCampaign updated = campaignRepository.findById(due.getCampaignId()).orElseThrow();
        assertEquals("SENT", updated.getStatus());
        assertEquals(1, notificationRepository.countByCampaign_CampaignId(due.getCampaignId()));
    }
}
