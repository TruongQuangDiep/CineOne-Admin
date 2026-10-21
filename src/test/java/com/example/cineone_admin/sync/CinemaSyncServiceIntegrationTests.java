package com.example.cineone_admin.sync;

import com.example.cineone_admin.dto.sync.*;
import com.example.cineone_admin.dto.sync.NormalizedCatalogDTO.*;
import com.example.cineone_admin.entity.*;
import com.example.cineone_admin.enums.*;
import com.example.cineone_admin.model.RoomType;
import com.example.cineone_admin.service.*;
import com.example.cineone_admin.tmdb.TmdbMovieService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static com.example.cineone_admin.enums.ProviderEntityType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:sync13;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"}, showSql = false)
@Import({CinemaSyncService.class, SyncRecordStore.class, SyncLogService.class,
        ProviderMappingService.class, ProviderMappingTransactionService.class, RoomTypeCatalogService.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CinemaSyncServiceIntegrationTests {
    @Autowired CinemaSyncService service;
    @Autowired ProviderMappingService mappings;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean CinemaSyncAdapterFactory factory;
    @MockitoBean TmdbMovieService tmdb;
    TransactionTemplate tx;
    CinemaSyncAdapter adapter;
    String code;
    Long providerId, tmdbProvider;
    final SyncRequestDTO request = new SyncRequestDTO(Set.of(CINEMA_BRAND, CINEMA, ROOM, SEAT, FNB_ITEM, SHOWTIME),
            SyncMode.FULL, null, null, null);

    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager);
        code = "TEST_" + UUID.randomUUID();
        providerId = tx.execute(s -> {
            var p = provider(code); em.persist(p); return p.getProviderId();
        });
        tmdbProvider = tx.execute(s -> {
            var all = em.createQuery("select p from DataProvider p where p.code='TMDB'", DataProvider.class).getResultList();
            if (!all.isEmpty()) return all.getFirst().getProviderId();
            var p = provider("TMDB"); em.persist(p); return p.getProviderId();
        });
        adapter = mock(CinemaSyncAdapter.class);
        when(factory.getAdapter(code)).thenReturn(adapter);
        when(tmdb.importMovie(anyLong())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0).toString();
            return mappings.resolveOrCreate(tmdbProvider, MOVIE, id, () -> {
                var movie = new Movie(); movie.setTitle("Movie " + id); movie.setDuration(100); return movie;
            });
        });
    }
    DataProvider provider(String code) {
        var p = new DataProvider(); p.setCode(code); p.setName(code); p.setProviderType("TEST"); return p;
    }
    ProviderReferenceDTO ref(ProviderEntityType type, String id) { return new ProviderReferenceDTO(code, type, id); }
    long movieId() { return Integer.toUnsignedLong(code.hashCode()) + 1; }
    ShowtimeDTO show(String id, int hour, BigDecimal price) {
        var start = LocalDate.of(2026, 11, 1).atTime(hour, 0);
        return new ShowtimeDTO(id, new ProviderReferenceDTO("TMDB", MOVIE, Long.toString(movieId())),
                ref(ROOM, "room"), start, start.plusMinutes(100), price, ShowtimeStatus.OPEN);
    }
    NormalizedCatalogDTO catalog(List<ShowtimeDTO> shows) {
        return new NormalizedCatalogDTO(
                List.of(new CinemaDTO("cinema", ref(CINEMA_BRAND, "brand"), "Cinema", "Address", null, null, null, CinemaStatus.ACTIVE)),
                List.of(new RoomDTO("room", ref(CINEMA, "cinema"), "Room 1", RoomType.STANDARD, RoomStatus.ACTIVE)),
                List.of(new SeatDTO("seat", ref(ROOM, "room"), "A", 1, SeatType.NORMAL, SeatStatus.ACTIVE)), shows,
                List.of(new FnbDTO("food", ref(CINEMA_BRAND, "brand"), ref(CINEMA, "cinema"), "Popcorn", null,
                        BigDecimal.TEN, null, FnbStatus.ACTIVE)), List.of(),
                List.of(new CinemaBrandDTO("brand", code, null, CinemaBrandStatus.ACTIVE)));
    }
    SyncRunResult run(NormalizedCatalogDTO data) {
        when(adapter.sync(eq(request), any(LocalDate.class))).thenReturn(SyncResultDTO.of(code, data, List.of()));
        return service.sync(code, request);
    }
    Long internal(ProviderEntityType type, String id) { return mappings.findInternalId(providerId, type, id).orElseThrow(); }

    @Test void importsMissingMovieBeforeShowtimeAndRepeatsWithoutDuplicates() {
        var data = catalog(List.of(show("show", 10, BigDecimal.TEN)));
        var first = run(data);
        assertEquals("SUCCESS", first.status()); assertEquals(6, first.success());
        Long id = internal(SHOWTIME, "show");
        var second = run(data);
        assertEquals(6, second.success()); assertEquals(id, internal(SHOWTIME, "show"));
        verify(tmdb, times(1)).importMovie(movieId());
        tx.executeWithoutResult(s -> {
            var log = em.find(ProviderSyncLog.class, second.syncLogId());
            assertEquals("SUCCESS", log.getStatus());
            var json = JsonMapper.builder().build().readTree(log.getMessage());
            assertEquals(6, json.get("total").asInt()); assertEquals(0, json.get("failed").asInt());
            assertEquals(0, json.get("errorsOmitted").asInt());
        });
    }

    @Test void conflictingShowtimeDoesNotUndoOthersAndPreservesAdminStatus() {
        run(catalog(List.of(show("show", 10, BigDecimal.TEN))));
        Long id = internal(SHOWTIME, "show");
        tx.executeWithoutResult(s -> em.find(Showtime.class, id).setStatus("CLOSED"));
        var result = run(catalog(List.of(show("show", 10, BigDecimal.ONE), show("later", 13, BigDecimal.TEN))));
        assertEquals("FAILED", result.status()); assertEquals(6, result.success()); assertEquals(1, result.failed());
        assertEquals("show", result.errors().getFirst().showtimeId());
        assertNotNull(internal(SHOWTIME, "later"));
        tx.executeWithoutResult(s -> {
            var original = em.find(Showtime.class, id);
            assertEquals(0, BigDecimal.TEN.compareTo(original.getBasePrice()));
            assertEquals("CLOSED", original.getStatus());
            assertEquals("FAILED", em.find(ProviderSyncLog.class, result.syncLogId()).getStatus());
        });
    }

    @Test void overlappingNewShowtimeIsRejectedButTouchingBoundaryIsAllowed() {
        var base = show("one", 10, BigDecimal.TEN);
        var adjacent = new ShowtimeDTO("three", base.movie(), base.room(), base.endTime(),
                base.endTime().plusMinutes(100), BigDecimal.TEN, ShowtimeStatus.OPEN);
        var result = run(catalog(List.of(base, show("two", 11, BigDecimal.TEN), adjacent)));
        assertEquals(7, result.success()); assertEquals(1, result.failed());
        assertTrue(mappings.findInternalId(providerId, SHOWTIME, "two").isEmpty());
        assertNotNull(internal(SHOWTIME, "three"));
    }

    @Test void failedMovieImportSkipsRelatedShowsOnlyAndIsAttemptedOnce() {
        doThrow(new IllegalStateException("secret API token")).when(tmdb).importMovie(movieId());
        var result = run(catalog(List.of(show("a", 10, BigDecimal.TEN), show("b", 13, BigDecimal.TEN))));
        assertEquals(5, result.success()); assertEquals(2, result.failed());
        verify(tmdb, times(1)).importMovie(movieId());
        assertFalse(result.errors().toString().contains("secret"));
        assertNotNull(internal(FNB_ITEM, "food"));
    }

    @Test void databaseConstraintFailureRollsBackRecordAndMappingThenContinues() {
        var good = new CinemaBrandDTO("good", code + "good", null, CinemaBrandStatus.ACTIVE);
        var bad = new CinemaBrandDTO("bad", code + "taken", null, CinemaBrandStatus.ACTIVE);
        tx.executeWithoutResult(s -> { var b = new CinemaBrand(); b.setName(bad.name()); em.persist(b); });
        var after = new CinemaBrandDTO("after", code + "after", null, CinemaBrandStatus.ACTIVE);
        var result = run(new NormalizedCatalogDTO(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(good, bad, after)));
        assertEquals(2, result.success()); assertEquals(1, result.failed());
        assertTrue(mappings.findInternalId(providerId, CINEMA_BRAND, "bad").isEmpty());
        assertNotNull(internal(CINEMA_BRAND, "good")); assertNotNull(internal(CINEMA_BRAND, "after"));
        tx.executeWithoutResult(s -> assertEquals("FAILED", em.find(ProviderSyncLog.class, result.syncLogId()).getStatus()));
    }

    @Test void missingParentRejectsChildrenWithoutInventingMappings() {
        var data = catalog(List.of());
        var result = run(new NormalizedCatalogDTO(data.cinemas(), data.rooms(), data.seats(), List.of(), data.fnbItems()));
        assertEquals(0, result.success()); assertEquals(4, result.failed());
        assertTrue(mappings.findInternalId(providerId, ROOM, "room").isEmpty());
    }

    @Test void failedParentUpdatePreventsChildrenUsingOldParent() {
        var data = catalog(List.of()); run(data);
        tx.executeWithoutResult(s -> { var b = new CinemaBrand(); b.setName(code + "taken"); em.persist(b); });
        var result = run(new NormalizedCatalogDTO(data.cinemas(), data.rooms(), data.seats(), List.of(), data.fnbItems(),
                List.of(), List.of(new CinemaBrandDTO("brand", code + "taken", null, CinemaBrandStatus.ACTIVE))));
        assertEquals(0, result.success()); assertEquals(5, result.failed());
        tx.executeWithoutResult(s -> assertEquals(code, em.find(CinemaBrand.class, internal(CINEMA_BRAND, "brand")).getName()));
    }

    @Test void adapterFatalFailureStillFinalizesLog() {
        when(adapter.sync(eq(request), any(LocalDate.class))).thenThrow(new IllegalStateException("failed source"));
        var result = service.sync(code, request);
        assertEquals("FAILED", result.status()); assertEquals(0, result.success());
        tx.executeWithoutResult(s -> assertEquals("FAILED", em.find(ProviderSyncLog.class, result.syncLogId()).getStatus()));
    }

    @Test void normalizationErrorsAreIncludedAlongsideCommittedRecords() {
        var data = catalog(List.of());
        when(adapter.sync(eq(request), any(LocalDate.class))).thenReturn(SyncResultDTO.of(code, data,
                List.of(new SyncErrorDTO(SHOWTIME, "invalid", "Sai giờ"))));
        var result = service.sync(code, request);
        assertEquals(6, result.total()); assertEquals(5, result.success()); assertEquals(1, result.failed());
        assertEquals("invalid", result.errors().getFirst().showtimeId());
    }

    @Test void inactiveProviderIsLoggedAndDoesNotReadSource() {
        tx.executeWithoutResult(s -> em.find(DataProvider.class, providerId).setStatus("INACTIVE"));
        assertEquals("FAILED", service.sync(code, request).status());
        verifyNoInteractions(adapter);
    }

    @Test void emptySnapshotDoesNotDeleteExistingData() {
        run(catalog(List.of(show("show", 10, BigDecimal.TEN))));
        Long id = internal(SHOWTIME, "show");
        assertEquals(0, run(NormalizedCatalogDTO.empty()).total());
        assertEquals(id, internal(SHOWTIME, "show"));
    }
}
