package com.example.cineone_admin.controller;

import com.example.cineone_admin.entity.*;
import com.example.cineone_admin.enums.*;
import com.example.cineone_admin.model.RoomType;
import com.example.cineone_admin.service.CatalogService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.math.BigDecimal;
import java.time.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog16;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
@Transactional
@WithMockUser(roles = "ADMIN")
class CatalogControllerIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    @Autowired CatalogService catalog;
    MockMvc mvc;
    DataProvider provider, stopped;
    Cinema cinema, other;
    Room room;
    Movie movie;
    Genre genre;
    Showtime showtime;
    FnbItem food;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        provider = provider("BETA", "ACTIVE"); stopped = provider("STOPPED", "INACTIVE");
        CinemaBrand brand = new CinemaBrand(); brand.setName("Beta Cinemas"); persist(brand);
        cinema = cinema(brand, "Beta Quang Trung"); other = cinema(brand, "Beta Trần Quang Khải");
        mapping(provider, "CINEMA", cinema.getCinemaId()); mapping(provider, "CINEMA", other.getCinemaId());
        persist(new RoomTypeCatalog(RoomType.of("VIP_PREMIUM")));
        room = new Room(); room.setCinema(cinema); room.setName("Room 01"); room.setRoomType(RoomType.of("VIP_PREMIUM")); persist(room);
        seat("A", 1, SeatType.NORMAL, SeatStatus.ACTIVE);
        seat("A", 3, SeatType.VIP, SeatStatus.MAINTENANCE);
        seat("B", 1, SeatType.SWEETBOX, SeatStatus.INACTIVE);
        movie = new Movie(); movie.setTitle("Phim mẫu <script>alert(1)</script>"); movie.setDuration(125);
        movie.setDescription("Mô tả phim mẫu"); movie.setStatus("NOW_SHOWING"); movie.setReleaseDate(LocalDate.of(2026, 10, 1)); persist(movie);
        genre = new Genre(); genre.setName("Hoạt hình"); persist(genre);
        MovieGenre link = new MovieGenre(); link.setMovie(movie); link.setGenre(genre);
        MovieGenreId linkId = new MovieGenreId(); linkId.setMovieId(movie.getMovieId()); linkId.setGenreId(genre.getGenreId());
        link.setId(linkId); persist(link);
        showtime = showtime(LocalDateTime.of(2026, 10, 25, 23, 0));
        food = food(cinema, "Combo đôi", "99000.00"); food(other, "Combo đôi", "109000.00");
        em.flush();
    }

    @Test void rendersAllViewsAndDetailsWithRealThymeleaf() throws Exception {
        String[] routes = {"/movies", "/movies/" + movie.getMovieId(), "/cinemas", "/cinemas/" + cinema.getCinemaId(),
                "/cinemas/" + cinema.getCinemaId() + "/rooms/" + room.getRoomId(), "/showtimes", "/fnb",
                "/fnb?cinemaId=" + cinema.getCinemaId()};
        for (int i = 0; i < routes.length; i++) {
            String html = mvc.perform(get(routes[i])).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertFalse(html.contains("<script>alert(1)</script>"));
            String preview = System.getProperty("catalog.preview.dir");
            if (preview != null) {
                Files.createDirectories(Path.of(preview)); Files.writeString(Path.of(preview, "screen-" + i + ".html"), html);
            }
        }
        mvc.perform(get("/movies")).andExpect(content().string(containsString("Hoạt hình")));
        mvc.perform(get(routes[4])).andExpect(content().string(containsString("VIP_PREMIUM")))
                .andExpect(content().string(containsString("A3")))
                .andExpect(content().string(containsString("grid-column: 3")))
                .andExpect(content().string(containsString("MAINTENANCE")));
        assertEquals(3, catalog.room(cinema.getCinemaId(), room.getRoomId()).seatCount());
        assertEquals(1, catalog.cinemas("Quang Trung", null, "ACTIVE", 0).getContent().getFirst().roomCount());
    }

    @Test void combinesMovieCinemaAndFoodFiltersAndKeepsLocalPrices() throws Exception {
        assertEquals(1, catalog.movies("phim mẫu", "NOW_SHOWING", genre.getGenreId(), 0).getTotalElements());
        assertEquals(0, catalog.movies("phim mẫu", "END", genre.getGenreId(), 0).getTotalElements());
        assertEquals(1, catalog.cinemas("Quang Trung", provider.getProviderId(), "ACTIVE", 0).getTotalElements());
        assertEquals(0, catalog.cinemas("Quang Trung", stopped.getProviderId(), "", 0).getTotalElements());
        assertEquals(2, catalog.fnb("Combo", "Beta", null, "ACTIVE", 0).getTotalElements());
        var local = catalog.fnb("Combo", "", cinema.getCinemaId(), "ACTIVE", 0);
        assertEquals(1, local.getTotalElements()); assertEquals(0, local.getContent().getFirst().getPrice().compareTo(new BigDecimal("99000")));
        mvc.perform(get("/fnb").param("cinemaId", cinema.getCinemaId().toString()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("99.000 ₫")))
                .andExpect(content().string(not(containsString("109.000 ₫"))));
    }

    @Test void dateFilterUsesStartDateWithExclusiveNextMidnight() {
        showtime(LocalDateTime.of(2026, 10, 25, 0, 0)); showtime(LocalDateTime.of(2026, 10, 26, 0, 0)); em.flush();
        assertEquals(2, catalog.showtimes("Quang Trung", "phim mẫu", LocalDate.of(2026, 10, 25), "OPEN", 0).getTotalElements());
        assertEquals(1, catalog.showtimes("", "", LocalDate.of(2026, 10, 26), "", 0).getTotalElements());
        assertEquals(0, catalog.showtimes("", "", null, "CANCELLED", 0).getTotalElements());
    }

    @Test void literalWildcardsEmptyDataAndPaginationWork() throws Exception {
        assertEquals(0, catalog.movies("%", "", null, 0).getTotalElements());
        assertEquals(0, catalog.cinemas("_", null, "", 0).getTotalElements());
        assertEquals(0, catalog.fnb("' or 1=1 --", "", null, "", 0).getTotalElements());
        for (int i = 0; i < 11; i++) { Movie m = new Movie(); m.setTitle("Paging " + i); m.setDuration(90); persist(m); }
        em.flush();
        mvc.perform(get("/movies").param("q", "Paging").param("status", "COMING_SOON"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("q=Paging")))
                .andExpect(content().string(containsString("status=COMING_SOON")))
                .andExpect(content().string(containsString("page=1")));
        assertEquals(1, catalog.movies("Paging", "", null, 1).getNumberOfElements());
        assertEquals(0, catalog.movies("", "", null, -1).getNumber());
        mvc.perform(get("/movies").param("page", "999")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Không tìm thấy phim")));
        for (String route : new String[]{"/movies", "/cinemas", "/showtimes", "/fnb"})
            mvc.perform(get(route).param("q", "missing").param("movie", "missing").param("cinema", "missing"))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("Không tìm thấy")));
    }

    @Test void stoppingProviderHidesCatalogAndDirectLinksWithoutDeletingRecords() throws Exception {
        provider.setStatus("INACTIVE"); em.flush();
        assertEquals(0, catalog.cinemas("", null, "", 0).getTotalElements());
        assertEquals(0, catalog.showtimes("", "", null, "", 0).getTotalElements());
        assertEquals(0, catalog.fnb("", "", null, "", 0).getTotalElements());
        mvc.perform(get("/cinemas/{id}", cinema.getCinemaId())).andExpect(status().isNotFound());
        mvc.perform(get("/cinemas/{cid}/rooms/{rid}", cinema.getCinemaId(), room.getRoomId())).andExpect(status().isNotFound());
        assertNotNull(em.find(Cinema.class, cinema.getCinemaId())); assertNotNull(em.find(FnbItem.class, food.getFnbId()));
        provider.setStatus("ACTIVE"); em.flush();
        assertEquals(2, catalog.cinemas("", null, "", 0).getTotalElements());
    }

    @Test void entityMappingsHideIndividualRecordsAndSharedSourcesRemainVisible() {
        mapping(stopped, "MOVIE", movie.getMovieId()); mapping(stopped, "ROOM", room.getRoomId());
        mapping(stopped, "FNB_ITEM", food.getFnbId()); em.flush();
        assertEquals(0, catalog.movies("", "", null, 0).getTotalElements());
        assertEquals(0, catalog.rooms(cinema.getCinemaId(), 0).getTotalElements());
        assertEquals(0, catalog.showtimes("", "", null, "", 0).getTotalElements());
        assertEquals(0, catalog.fnb("", "", cinema.getCinemaId(), "", 0).getTotalElements());
        mapping(provider, "MOVIE", movie.getMovieId()); em.flush();
        assertEquals(1, catalog.movies("", "", null, 0).getTotalElements());
        provider.setStatus("ERROR"); em.flush();
        assertEquals(1, catalog.movies("", "", null, 0).getTotalElements());
    }

    @Test void validatesParametersMissingIdsAndRoomOwnership() throws Exception {
        for (String route : new String[]{"/movies", "/cinemas", "/showtimes", "/fnb"})
            mvc.perform(get(route).param("status", "INVALID")).andExpect(status().isBadRequest());
        mvc.perform(get("/showtimes").param("date", "bad-date")).andExpect(status().isBadRequest());
        mvc.perform(get("/movies").param("q", "x".repeat(256))).andExpect(status().isBadRequest());
        mvc.perform(get("/movies/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/fnb").param("cinemaId", "999999")).andExpect(status().isNotFound());
        mvc.perform(get("/cinemas/{cid}/rooms/{rid}", other.getCinemaId(), room.getRoomId())).andExpect(status().isNotFound());
    }

    @Test void requiresAdminAndDoesNotExposeWriteEndpoints() throws Exception {
        for (String route : new String[]{"/movies", "/cinemas", "/showtimes", "/fnb", "/movies/" + movie.getMovieId(),
                "/cinemas/" + cinema.getCinemaId(), "/cinemas/" + cinema.getCinemaId() + "/rooms/" + room.getRoomId()}) {
            mvc.perform(get(route).with(anonymous())).andExpect(status().is3xxRedirection());
            mvc.perform(get(route).with(user("member").roles("USER"))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/movies").with(csrf())).andExpect(status().isMethodNotAllowed());
    }

    @Test void detailsContainNoLazyDependenciesAfterServiceReturns() {
        var cinemas = catalog.cinemas("", null, "", 0); var shows = catalog.showtimes("", "", null, "", 0);
        var foods = catalog.fnb("", "", null, "", 0); var roomDetail = catalog.room(cinema.getCinemaId(), room.getRoomId());
        em.clear();
        assertEquals("Beta Cinemas", cinemas.getContent().getFirst().cinema().getBrand().getName());
        assertEquals("Beta Quang Trung", shows.getContent().getFirst().getRoom().getCinema().getName());
        assertEquals("Beta Quang Trung", foods.getContent().getFirst().getCinema().getName());
        assertEquals(2, roomDetail.rows().size());
    }

    private <T> T persist(T entity) { em.persist(entity); return entity; }
    private DataProvider provider(String code, String status) {
        DataProvider p = new DataProvider(); p.setCode(code); p.setName(code); p.setProviderType("CINEMA"); p.setStatus(status); return persist(p);
    }
    private Cinema cinema(CinemaBrand brand, String name) {
        Cinema c = new Cinema(); c.setBrand(brand); c.setName(name); c.setAddress("645 Quang Trung, Gò Vấp"); return persist(c);
    }
    private void seat(String row, int number, SeatType type, SeatStatus status) {
        Seat s = new Seat(); s.setRoom(room); s.setSeatRow(row); s.setSeatNumber(number); s.setSeatType(type); s.setStatus(status); persist(s);
    }
    private Showtime showtime(LocalDateTime start) {
        Showtime s = new Showtime(); s.setMovie(movie); s.setRoom(room); s.setStartTime(start); s.setEndTime(start.plusMinutes(125)); s.setBasePrice(new BigDecimal("75000")); return persist(s);
    }
    private FnbItem food(Cinema c, String name, String price) {
        FnbItem f = new FnbItem(); f.setCinema(c); f.setBrand(c.getBrand()); f.setName(name); f.setPrice(new BigDecimal(price)); return persist(f);
    }
    private void mapping(DataProvider p, String type, Long id) {
        ProviderMapping m = new ProviderMapping(); m.setProvider(p); m.setEntityType(type); m.setInternalId(id); m.setExternalId(type + "_" + id); persist(m);
    }
}
