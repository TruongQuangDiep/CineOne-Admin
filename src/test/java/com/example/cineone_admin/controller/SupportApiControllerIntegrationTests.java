package com.example.cineone_admin.controller;

import com.example.cineone_admin.entity.*;
import com.example.cineone_admin.enums.BookingStatus;
import com.example.cineone_admin.model.RoomType;
import com.example.cineone_admin.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:supportapi_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Transactional
class SupportApiControllerIntegrationTests {

    @Autowired private WebApplicationContext context;
    @Autowired private EntityManager entityManager;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipTierRepository tierRepository;
    @Autowired private SupportRequestRepository supportRepository;
    @Autowired private KnowledgeBaseEntryRepository kbRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private MovieRepository movieRepository;

    private MockMvc mvc;
    private User activeUser;
    private User otherUser;
    private Booking sampleBooking;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        supportRepository.deleteAll();
        kbRepository.deleteAll();
        bookingRepository.deleteAll();
        userRepository.deleteAll();
        tierRepository.deleteAll();

        MembershipTier tier = new MembershipTier();
        tier.setName("STANDARD");
        tier.setMinSpending(BigDecimal.ZERO);
        tier.setStatus("ACTIVE");
        tier = tierRepository.saveAndFlush(tier);

        activeUser = new User();
        activeUser.setEmail("member@cineone.vn");
        activeUser.setFullName("Nguyễn Minh Quân");
        activeUser.setPasswordHash("encoded_pwd");
        activeUser.setRole("USER");
        activeUser.setStatus("ACTIVE");
        activeUser.setMembershipTier(tier);
        activeUser = userRepository.saveAndFlush(activeUser);

        otherUser = new User();
        otherUser.setEmail("other@cineone.vn");
        otherUser.setFullName("Lê Hoàng Anh");
        otherUser.setPasswordHash("encoded_pwd");
        otherUser.setRole("USER");
        otherUser.setStatus("ACTIVE");
        otherUser.setMembershipTier(tier);
        otherUser = userRepository.saveAndFlush(otherUser);

        entityManager.persist(new RoomTypeCatalog(RoomType.STANDARD));

        CinemaBrand brand = new CinemaBrand();
        brand.setName("CGV Cinemas");
        entityManager.persist(brand);

        Cinema cinema = new Cinema();
        cinema.setBrand(brand);
        cinema.setName("CGV Vincom Đồng Khởi");
        cinema.setAddress("72 Lê Thánh Tôn, Quận 1");
        cinema.setStatus("ACTIVE");
        entityManager.persist(cinema);

        Room room = new Room();
        room.setCinema(cinema);
        room.setRoomType(RoomType.STANDARD);
        room.setName("Cinema 01");
        entityManager.persist(room);

        Movie movie = new Movie();
        movie.setTitle("Lật Mặt 7");
        movie.setDuration(138);
        movie = movieRepository.saveAndFlush(movie);

        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setRoom(room);
        showtime.setStartTime(LocalDateTime.now().plusHours(2));
        showtime.setEndTime(LocalDateTime.now().plusHours(4));
        showtime.setBasePrice(new BigDecimal("90000.00"));
        showtime.setStatus("OPEN");
        entityManager.persist(showtime);

        sampleBooking = new Booking();
        sampleBooking.setUser(activeUser);
        sampleBooking.setShowtime(showtime);
        sampleBooking.setBookingCode("CINE-BKG-2026");
        sampleBooking.setStatus(BookingStatus.CONFIRMED);
        sampleBooking.setTotalAmount(new BigDecimal("180000.00"));
        sampleBooking = bookingRepository.saveAndFlush(sampleBooking);

        KnowledgeBaseEntry faq1 = new KnowledgeBaseEntry();
        faq1.setTitle("Làm thế nào để đặt vé trên CineOne?");
        faq1.setCategory("FAQ");
        faq1.setContent("Chọn phim, chọn suất chiếu, chọn ghế và thanh toán qua mã VietQR.");
        faq1.setStatus("ACTIVE");
        kbRepository.saveAndFlush(faq1);

        KnowledgeBaseEntry faq2 = new KnowledgeBaseEntry();
        faq2.setTitle("Chính sách đổi trả và hoàn vé");
        faq2.setCategory("CHÍNH SÁCH");
        faq2.setContent("Vé đã mua không hỗ trợ hoàn hủy trừ trường hợp suất chiếu bị thay đổi từ phía rạp.");
        faq2.setStatus("ACTIVE");
        kbRepository.saveAndFlush(faq2);

        KnowledgeBaseEntry inactiveFaq = new KnowledgeBaseEntry();
        inactiveFaq.setTitle("Bài viết đang tạm ẩn");
        inactiveFaq.setCategory("FAQ");
        inactiveFaq.setContent("Nội dung ẩn.");
        inactiveFaq.setStatus("INACTIVE");
        kbRepository.saveAndFlush(inactiveFaq);
    }

    @Test
    void getFaqPublicAccessAndFiltering() throws Exception {
        mvc.perform(get("/api/v1/faq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].title", is("Làm thế nào để đặt vé trên CineOne?")));

        mvc.perform(get("/api/v1/faq").param("category", "CHÍNH SÁCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].category", is("CHÍNH SÁCH")));

        mvc.perform(get("/api/v1/faq").param("q", "VietQR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title", containsString("đặt vé")));
    }

    @Test
    void supportEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/support/my-requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));

        mvc.perform(post("/api/v1/support")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"KHIẾU NẠI\",\"category\":\"THANH TOÁN\",\"content\":\"Test\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "member@cineone.vn", roles = "USER")
    void createSupportRequestAndFetchMyRequestsAndDetail() throws Exception {
        String payload = String.format(
                "{\"type\":\"KHIẾU NẠI\",\"category\":\"THANH TOÁN\",\"content\":\"Đã chuyển khoản thành công nhưng chưa nhận được mã QR vé.\",\"bookingId\":%d}",
                sampleBooking.getBookingId()
        );

        mvc.perform(post("/api/v1/support")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.data.type", is("KHIẾU NẠI")))
                .andExpect(jsonPath("$.data.category", is("THANH TOÁN")))
                .andExpect(jsonPath("$.data.status", is("PENDING")))
                .andExpect(jsonPath("$.data.bookingCode", is("CINE-BKG-2026")));

        mvc.perform(get("/api/v1/support/my-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].bookingCode", is("CINE-BKG-2026")));

        SupportRequest saved = supportRepository.findAll().get(0);

        mvc.perform(get("/api/v1/support/{id}", saved.getRequestId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.requestId", is(saved.getRequestId().intValue())))
                .andExpect(jsonPath("$.data.content", containsString("Đã chuyển khoản thành công")));
    }

    @Test
    @WithMockUser(username = "other@cineone.vn", roles = "USER")
    void preventsAccessingAnotherUsersSupportRequest() throws Exception {
        SupportRequest sr = new SupportRequest();
        sr.setUser(activeUser);
        sr.setType("HỖ TRỢ");
        sr.setCategory("VÉ");
        sr.setContent("Yêu cầu riêng tư của Nguyễn Minh Quân");
        sr.setStatus("PENDING");
        sr = supportRepository.saveAndFlush(sr);

        mvc.perform(get("/api/v1/support/{id}", sr.getRequestId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }
}

