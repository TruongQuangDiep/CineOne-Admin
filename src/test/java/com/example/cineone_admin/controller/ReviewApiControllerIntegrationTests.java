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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reviewapi_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect" })
@Transactional
class ReviewApiControllerIntegrationTests {

    @Autowired
    WebApplicationContext context;
    @Autowired
    EntityManager entityManager;
    @Autowired
    UserRepository users;
    @Autowired
    MembershipTierRepository tiers;
    @Autowired
    MovieRepository movies;
    @Autowired
    ReviewRepository reviews;
    @Autowired
    TicketRepository tickets;

    MockMvc mvc;
    User activeUser;
    Movie sampleMovie;
    Review visibleReview;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        MembershipTier tier = new MembershipTier();
        tier.setName("STANDARD");
        tier.setMinSpending(BigDecimal.ZERO);
        tier.setStatus("ACTIVE");
        tier = tiers.saveAndFlush(tier);

        activeUser = new User();
        activeUser.setEmail("tinh@cineone.vn");
        activeUser.setFullName("Trần Trí Tính");
        activeUser.setPasswordHash("encoded_pwd");
        activeUser.setRole("USER");
        activeUser.setStatus("ACTIVE");
        activeUser.setMembershipTier(tier);
        activeUser = users.saveAndFlush(activeUser);

        sampleMovie = new Movie();
        sampleMovie.setTitle("Đất Rừng Phương Nam");
        sampleMovie.setDuration(134);
        sampleMovie = movies.saveAndFlush(sampleMovie);

        visibleReview = new Review();
        visibleReview.setUser(activeUser);
        visibleReview.setMovie(sampleMovie);
        visibleReview.setRating((byte) 5);
        visibleReview.setContent("Phim rất xúc động và cảnh quay thiên nhiên tuyệt đẹp!");
        visibleReview.setStatus("VISIBLE");
        visibleReview = reviews.saveAndFlush(visibleReview);
    }

    @Test
    void getMovieReviewsPublicAccess() throws Exception {
        mvc.perform(get("/api/v1/reviews/movie/{movieId}", sampleMovie.getMovieId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.data.movieId", is(sampleMovie.getMovieId().intValue())))
                .andExpect(jsonPath("$.data.averageRating", is(5.0)))
                .andExpect(jsonPath("$.data.reviews", hasSize(1)))
                .andExpect(jsonPath("$.data.reviews[0].userName", is("Trần Trí Tính")))
                .andExpect(jsonPath("$.data.reviews[0].rating", is(5)));
    }

    @Test
    @WithMockUser(username = "tinh@cineone.vn", roles = "USER")
    void checkEligibilityWithoutTicketReturnsFalse() throws Exception {
        // Người dùng chưa có vé xem phim này -> Không đủ điều kiện (RT2)
        mvc.perform(get("/api/v1/reviews/eligibility/{movieId}", sampleMovie.getMovieId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eligible", is(false)))
                .andExpect(jsonPath("$.data.alreadyReviewed", is(true)));
    }

    @Test
    @WithMockUser(username = "tinh@cineone.vn", roles = "USER")
    void checkEligibilityWithValidEndedTicketReturnsTrue() throws Exception {
        // Tạo vé hợp lệ với suất chiếu đã kết thúc trong quá khứ
        entityManager.persist(new RoomTypeCatalog(RoomType.STANDARD));

        CinemaBrand brand = new CinemaBrand();
        brand.setName("Beta Cinemas");
        entityManager.persist(brand);

        Cinema cinema = new Cinema();
        cinema.setBrand(brand);
        cinema.setName("Beta Quang Trung");
        cinema.setAddress("645 Quang Trung, Gò Vấp");
        cinema.setStatus("ACTIVE");
        entityManager.persist(cinema);

        Room room = new Room();
        room.setCinema(cinema);
        room.setRoomType(RoomType.STANDARD);
        room.setName("Cinema 1");
        entityManager.persist(room);

        Showtime showtime = new Showtime();
        showtime.setMovie(sampleMovie);
        showtime.setRoom(room);
        showtime.setStartTime(LocalDateTime.now().minusHours(4));
        showtime.setEndTime(LocalDateTime.now().minusHours(2));
        showtime.setBasePrice(new BigDecimal("75000.00"));
        showtime.setStatus("ENDED");
        entityManager.persist(showtime);

        Booking booking = new Booking();
        booking.setUser(activeUser);
        booking.setShowtime(showtime);
        booking.setBookingCode("BKG-REVIEW-TEST");
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalAmount(new BigDecimal("75000.00"));
        entityManager.persist(booking);

        Ticket ticket = new Ticket();
        ticket.setBooking(booking);
        ticket.setTicketCode("TKT-REVIEW-TEST");
        ticket.setMovieTitle(sampleMovie.getTitle());
        ticket.setCinemaName(cinema.getName());
        ticket.setRoomName(room.getName());
        ticket.setShowtimeStart(showtime.getStartTime());
        ticket.setSeatInfo("A01");
        ticket.setTotalAmount(new BigDecimal("75000.00"));
        ticket.setStatus("VALID");
        tickets.saveAndFlush(ticket);

        mvc.perform(get("/api/v1/reviews/eligibility/{movieId}", sampleMovie.getMovieId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eligible", is(true)))
                .andExpect(jsonPath("$.data.alreadyReviewed", is(true)));
    }

    @Test
    @WithMockUser(username = "tinh@cineone.vn", roles = "USER")
    void getMyReviewsReturnsUserReviews() throws Exception {
        mvc.perform(get("/api/v1/reviews/my-reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].movieTitle", is("Đất Rừng Phương Nam")))
                .andExpect(jsonPath("$.data[0].rating", is(5)));
    }
}
