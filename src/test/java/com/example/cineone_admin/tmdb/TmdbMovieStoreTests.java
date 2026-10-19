package com.example.cineone_admin.tmdb;

import com.example.cineone_admin.entity.*;
import com.example.cineone_admin.enums.ProviderEntityType;
import com.example.cineone_admin.repository.*;
import com.example.cineone_admin.service.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:tmdb;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.show-sql=false",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"}, showSql = false)
@Import({TmdbMovieStore.class, ProviderMappingService.class, ProviderMappingTransactionService.class,
        RoomTypeCatalogService.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TmdbMovieStoreTests {
    @Autowired TmdbMovieStore store;
    @Autowired DataProviderRepository providers;
    @Autowired ProviderMappingRepository mappings;
    @Autowired ProviderMappingService mappingService;
    @Autowired MovieRepository movies;
    @Autowired GenreRepository genres;
    @Autowired MovieGenreRepository links;
    @Autowired PlatformTransactionManager manager;
    private final TmdbMovieAdapter adapter = new TmdbMovieAdapter(new TmdbProperties());
    private Long providerId;

    @BeforeEach void setup() {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            links.deleteAllInBatch();
            mappings.deleteAllInBatch();
            movies.deleteAllInBatch();
            genres.deleteAllInBatch();
            providers.deleteAllInBatch();
            var provider = new DataProvider();
            provider.setCode("TMDB");
            provider.setName("The Movie Database");
            provider.setProviderType("API");
            providerId = providers.saveAndFlush(provider).getProviderId();
        });
    }

    private TmdbMovieAdapter.EnrichedMovie source(long id, String poster, TmdbDtos.Genre... incoming) {
        return adapter.normalize(TmdbMovieAdapterTests.detail(id, poster, List.of(incoming)));
    }

    @Test void repeatedImportsReuseMovieGenresAndMappings() {
        var data = source(42, "/a.jpg", new TmdbDtos.Genre(28L, "Action"));
        Long id = store.save(data);
        assertEquals(id, store.save(data));
        assertEquals(1, movies.count());
        assertEquals(1, genres.count());
        assertEquals(1, links.count());
        assertEquals(2, mappings.count());
        assertEquals(id, mappingService.findInternalId(providerId, ProviderEntityType.MOVIE, "42").orElseThrow());
        assertNotNull(movies.findById(id).orElseThrow().getDirector());
    }

    @Test void fillsMissingFieldsButPreservesManualDataStatusAndExistingGenres() {
        Long id = store.save(source(42, null, new TmdbDtos.Genre(28L, "Action")));
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            var movie = movies.findById(id).orElseThrow();
            movie.setTitle("Tên quản trị viên sửa");
            movie.setDescription("Mô tả thủ công");
            movie.setStatus("NOW_SHOWING");
            movie.setAgeRating("T18");
        });
        store.save(source(42, "/new.jpg", new TmdbDtos.Genre(35L, "Comedy")));
        var result = movies.findById(id).orElseThrow();
        assertEquals("Tên quản trị viên sửa", result.getTitle());
        assertEquals("Mô tả thủ công", result.getDescription());
        assertEquals("NOW_SHOWING", result.getStatus());
        assertEquals("T18", result.getAgeRating());
        assertTrue(result.getPosterUrl().endsWith("/new.jpg"));
        assertEquals(2, links.count());
        store.save(source(42, null));
        assertTrue(movies.findById(id).orElseThrow().getPosterUrl().endsWith("/new.jpg"));
        assertEquals(2, links.count());
    }

    @Test void adoptsAnExistingGenreAndUsesStableExternalIdAfterNameChanges() {
        var genre = new Genre();
        genre.setName("Action");
        Long genreId = genres.saveAndFlush(genre).getGenreId();
        store.save(source(42, null, new TmdbDtos.Genre(28L, "Action")));
        store.save(source(43, null, new TmdbDtos.Genre(28L, "Hành động")));
        assertEquals(1, genres.count());
        assertEquals(genreId, mappingService.findInternalId(providerId, ProviderEntityType.GENRE, "28").orElseThrow());
        assertEquals("Action", genres.findById(genreId).orElseThrow().getName());
    }

    @Test void conflictingGenreMappingRollsBackMovieAndAllPartialWrites() {
        store.save(source(42, null, new TmdbDtos.Genre(28L, "Action")));
        assertThrows(RuntimeException.class, () -> store.save(source(43, "/b.jpg",
                new TmdbDtos.Genre(35L, "Comedy"), new TmdbDtos.Genre(99L, "Action"))));
        assertEquals(1, movies.count());
        assertEquals(1, genres.count());
        assertEquals(1, links.count());
        assertEquals(2, mappings.count());
        assertTrue(mappingService.findInternalId(providerId, ProviderEntityType.MOVIE, "43").isEmpty());
    }

    @Test void inactiveProviderDoesNotWrite() {
        var provider = providers.findById(providerId).orElseThrow();
        provider.setStatus("INACTIVE");
        providers.saveAndFlush(provider);
        assertThrows(IllegalStateException.class, () -> store.save(source(42, null)));
        assertEquals(0, movies.count());
    }

    @Test void concurrentImportsDoNotDuplicateRecords() throws Exception {
        var data = source(42, "/a.jpg", new TmdbDtos.Genre(28L, "Action"));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            Callable<Long> task = () -> { ready.countDown(); start.await(); return store.save(data); };
            var first = executor.submit(task);
            var second = executor.submit(task);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, movies.count());
        assertEquals(1, genres.count());
        assertEquals(1, links.count());
    }
}
