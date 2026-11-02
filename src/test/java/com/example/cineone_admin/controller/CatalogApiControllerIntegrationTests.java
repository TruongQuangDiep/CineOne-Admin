package com.example.cineone_admin.controller;

import com.example.cineone_admin.entity.*;
import com.example.cineone_admin.enums.SeatStatus;
import com.example.cineone_admin.enums.SeatType;
import com.example.cineone_admin.model.RoomType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:scrum23_catalog_api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Transactional
class CatalogApiControllerIntegrationTests {

    @Autowired private WebApplicationContext context;
    @Autowired private EntityManager em;

    private MockMvc mvc;
    private DataProvider activeProvider;
    private DataProvider inactiveProvider;
    private Cinema hcmCinema;
    private Cinema hanoiCinema;
    private Room room;
    private Movie nowShowingMovie;
    private Movie comingSoonMovie;
    private Genre actionGenre;
    private Showtime showtime;
    private FnbItem popcornCombo;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        activeProvider = provider("BETA", "ACTIVE");
        inactiveProvider = provider("STOPPED", "INACTIVE");

        CinemaBrand brand = new CinemaBrand();
        brand.setName("Beta Cinemas");
        brand.setLogoUrl("https://cdn.cineone.vn/beta.png");
        persist(brand);

        hcmCinema = cinema(brand, "Beta Quang Trung", "645 Quang Trung, Quận Gò Vấp, TP. Hồ Chí Minh");
        hanoiCinema = cinema(brand, "Beta Thanh Xuân", "Số 2 Lê Văn Thiêm, Quận Thanh Xuân, Hà Nội");
        mapping(activeProvider, "CINEMA", hcmCinema.getCinemaId());
        mapping(activeProvider, "CINEMA", hanoiCinema.getCinemaId());

        persist(new RoomTypeCatalog(RoomType.of("STANDARD")));
        room = new Room();
        room.setCinema(hcmCinema);
        room.setName("Room 01");
        room.setRoomType(RoomType.of("STANDARD"));
        persist(room);

        Seat seat = new Seat();
        seat.setRoom(room);
        seat.setSeatRow("A");
        seat.setSeatNumber(1);
        seat.setSeatType(SeatType.NORMAL);
        seat.setStatus(SeatStatus.ACTIVE);
        persist(seat);

        nowShowingMovie = new Movie();
        nowShowingMovie.setTitle("Lật Mặt 8");
        nowShowingMovie.setDescription("Phim hành động kịch tính");
        nowShowingMovie.setDuration(120);
        nowShowingMovie.setAgeRating("T16");
        nowShowingMovie.setCountry("Việt Nam");
        nowShowingMovie.setDirector("Lý Hải");
        nowShowingMovie.setCastInfo("Quốc Cường, Long Đẹp Trai");
        nowShowingMovie.setPosterUrl("https://cdn.cineone.vn/latmat8.jpg");
        nowShowingMovie.setTrailerUrl("https://youtu.be/latmat8");
        nowShowingMovie.setReleaseDate(LocalDate.of(2026, 10, 20));
        nowShowingMovie.setStatus("NOW_SHOWING");
        persist(nowShowingMovie);

        comingSoonMovie = new Movie();
        comingSoonMovie.setTitle("Thám Tử Kiên 2");
        comingSoonMovie.setDescription("Phim trinh thám sắp chiếu");
        comingSoonMovie.setDuration(135);
        comingSoonMovie.setReleaseDate(LocalDate.of(2026, 12, 1));
        comingSoonMovie.setStatus("COMING_SOON");
        persist(comingSoonMovie);

        actionGenre = new Genre();
        actionGenre.setName("Hành động");
        persist(actionGenre);

        MovieGenre link = new MovieGenre();
        link.setMovie(nowShowingMovie);
        link.setGenre(actionGenre);
        MovieGenreId linkId = new MovieGenreId();
        linkId.setMovieId(nowShowingMovie.getMovieId());
        linkId.setGenreId(actionGenre.getGenreId());
        link.setId(linkId);
        persist(link);

        MembershipTier tier = new MembershipTier();
        tier.setName("STANDARD");
        tier.setMinSpending(BigDecimal.ZERO);
        tier.setStatus("ACTIVE");
        persist(tier);

        User reviewer = new User();
        reviewer.setEmail("reviewer@cineone.vn");
        reviewer.setFullName("Nguyễn Văn A");
        reviewer.setPasswordHash("hash");
        reviewer.setPhone("0901234567");
        reviewer.setRole("USER");
        reviewer.setStatus("ACTIVE");
        reviewer.setMembershipTier(tier);
        persist(reviewer);

        Review review1 = new Review();
        review1.setMovie(nowShowingMovie);
        review1.setUser(reviewer);
        review1.setRating((byte) 5);
        review1.setContent("Phim rất hay!");
        review1.setStatus("VISIBLE");
        persist(review1);

        Review review2 = new Review();
        review2.setMovie(nowShowingMovie);
        review2.setUser(reviewer);
        review2.setRating((byte) 4);
        review2.setContent("Kịch bản tốt");
        review2.setStatus("VISIBLE");
        persist(review2);

        showtime = new Showtime();
        showtime.setMovie(nowShowingMovie);
        showtime.setRoom(room);
        showtime.setStartTime(LocalDateTime.of(2026, 11, 2, 19, 30));
        showtime.setEndTime(LocalDateTime.of(2026, 11, 2, 21, 30));
        showtime.setBasePrice(new BigDecimal("85000.00"));
        showtime.setStatus("OPEN");
        persist(showtime);

        popcornCombo = new FnbItem();
        popcornCombo.setCinema(hcmCinema);
        popcornCombo.setBrand(brand);
        popcornCombo.setName("Combo Bắp Nước Đôi");
        popcornCombo.setDescription("1 Bắp lớn + 2 Nước ngọt");
        popcornCombo.setPrice(new BigDecimal("99000.00"));
        popcornCombo.setImageUrl("https://cdn.cineone.vn/combo.jpg");
        popcornCombo.setStatus("ACTIVE");
        persist(popcornCombo);

        FnbItem hanoiFnb = new FnbItem();
        hanoiFnb.setCinema(hanoiCinema);
        hanoiFnb.setBrand(brand);
        hanoiFnb.setName("Combo Hà Nội");
        hanoiFnb.setPrice(new BigDecimal("109000.00"));
        hanoiFnb.setStatus("ACTIVE");
        persist(hanoiFnb);

        em.flush();
    }

    // === SUBTASK SCRUM-84: MOVIES & MOVIE DETAIL API TESTS ===

    @Test
    void anonymousUserCanListMoviesWithSearchStatusGenreAndPagination() throws Exception {
        mvc.perform(get("/api/v1/movies")
                        .with(anonymous())
                        .param("q", "Lật Mặt")
                        .param("status", "NOW_SHOWING")
                        .param("genreId", actionGenre.getGenreId().toString())
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].movieId").value(nowShowingMovie.getMovieId()))
                .andExpect(jsonPath("$.data[0].title").value("Lật Mặt 8"))
                .andExpect(jsonPath("$.data[0].poster").value("https://cdn.cineone.vn/latmat8.jpg"))
                .andExpect(jsonPath("$.data[0].trailer").value("https://youtu.be/latmat8"))
                .andExpect(jsonPath("$.data[0].duration").value(120))
                .andExpect(jsonPath("$.data[0].releaseDate").value("2026-10-20"))
                .andExpect(jsonPath("$.data[0].genres").value("Hành động"))
                .andExpect(jsonPath("$.data[0].rating").value(4.5))
                .andExpect(jsonPath("$.data[0].status").value("NOW_SHOWING"));

        mvc.perform(get("/api/v1/movies")
                        .with(anonymous())
                        .param("status", "COMING_SOON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("Thám Tử Kiên 2"));
    }

    @Test
    void anonymousUserCanGetMovieDetailAndReceives404WhenNotFound() throws Exception {
        mvc.perform(get("/api/v1/movies/{id}", nowShowingMovie.getMovieId()).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.movieId").value(nowShowingMovie.getMovieId()))
                .andExpect(jsonPath("$.data.title").value("Lật Mặt 8"))
                .andExpect(jsonPath("$.data.description").value("Phim hành động kịch tính"))
                .andExpect(jsonPath("$.data.director").value("Lý Hải"))
                .andExpect(jsonPath("$.data.castInfo").value("Quốc Cường, Long Đẹp Trai"))
                .andExpect(jsonPath("$.data.genres").value("Hành động"))
                .andExpect(jsonPath("$.data.rating").value(4.5));

        mvc.perform(get("/api/v1/movies/{id}", 999999L).with(anonymous()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(404));
    }

    // === SUBTASK SCRUM-85: CINEMAS, SHOWTIMES & FNB API TESTS ===

    @Test
    void anonymousUserCanListCinemasFilterByCityAndGetCinemaDetail() throws Exception {
        mvc.perform(get("/api/v1/cinemas")
                        .with(anonymous())
                        .param("city", "Hồ Chí Minh")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].cinemaId").value(hcmCinema.getCinemaId()))
                .andExpect(jsonPath("$.data[0].name").value("Beta Quang Trung"))
                .andExpect(jsonPath("$.data[0].city").value("TP. Hồ Chí Minh"))
                .andExpect(jsonPath("$.data[0].roomCount").value(1));

        mvc.perform(get("/api/v1/cinemas/{id}", hcmCinema.getCinemaId()).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.cinemaId").value(hcmCinema.getCinemaId()))
                .andExpect(jsonPath("$.data.name").value("Beta Quang Trung"))
                .andExpect(jsonPath("$.data.roomCount").value(1));

        mvc.perform(get("/api/v1/cinemas/{id}", 999999L).with(anonymous()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void anonymousUserCanGetCinemaFnbsIsolatedByCinemaId() throws Exception {
        mvc.perform(get("/api/v1/cinemas/{id}/fnbs", hcmCinema.getCinemaId()).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].fnbId").value(popcornCombo.getFnbId()))
                .andExpect(jsonPath("$.data[0].name").value("Combo Bắp Nước Đôi"))
                .andExpect(jsonPath("$.data[0].price").value(99000.0))
                .andExpect(jsonPath("$.data[0].cinemaId").value(hcmCinema.getCinemaId()));

        mvc.perform(get("/api/v1/cinemas/{id}/fnbs", 999999L).with(anonymous()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void anonymousUserCanFilterShowtimesByMovieIdCinemaIdAndDate() throws Exception {
        mvc.perform(get("/api/v1/showtimes")
                        .with(anonymous())
                        .param("movieId", nowShowingMovie.getMovieId().toString())
                        .param("cinemaId", hcmCinema.getCinemaId().toString())
                        .param("date", "2026-11-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].showtimeId").value(showtime.getShowtimeId()))
                .andExpect(jsonPath("$.data[0].movieId").value(nowShowingMovie.getMovieId()))
                .andExpect(jsonPath("$.data[0].cinemaId").value(hcmCinema.getCinemaId()))
                .andExpect(jsonPath("$.data[0].roomId").value(room.getRoomId()))
                .andExpect(jsonPath("$.data[0].roomName").value("Room 01"))
                .andExpect(jsonPath("$.data[0].startTime").value("2026-11-02T19:30:00"))
                .andExpect(jsonPath("$.data[0].endTime").value("2026-11-02T21:30:00"))
                .andExpect(jsonPath("$.data[0].price").value(85000.0));

        mvc.perform(get("/api/v1/showtimes")
                        .with(anonymous())
                        .param("movieId", comingSoonMovie.getMovieId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void invalidParametersReturnStandardBadRequestJson() throws Exception {
        mvc.perform(get("/api/v1/movies").with(anonymous()).param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(get("/api/v1/cinemas").with(anonymous()).param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(get("/api/v1/showtimes").with(anonymous()).param("date", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400));
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private DataProvider provider(String code, String status) {
        DataProvider p = new DataProvider();
        p.setCode(code);
        p.setName(code);
        p.setProviderType("CINEMA");
        p.setStatus(status);
        return persist(p);
    }

    private Cinema cinema(CinemaBrand brand, String name, String address) {
        Cinema c = new Cinema();
        c.setBrand(brand);
        c.setName(name);
        c.setAddress(address);
        c.setStatus("ACTIVE");
        return persist(c);
    }

    private void mapping(DataProvider p, String type, Long id) {
        ProviderMapping m = new ProviderMapping();
        m.setProvider(p);
        m.setEntityType(type);
        m.setInternalId(id);
        m.setExternalId(type + "_" + id);
        persist(m);
    }
}

