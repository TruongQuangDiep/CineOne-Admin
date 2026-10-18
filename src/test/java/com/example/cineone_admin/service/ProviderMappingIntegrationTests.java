package com.example.cineone_admin.service;

import com.example.cineone_admin.entity.*;
import com.example.cineone_admin.enums.*;
import com.example.cineone_admin.model.RoomType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.example.cineone_admin.dto.sync.EntityCreationPair;
import com.example.cineone_admin.exception.ProviderMappingException;
import org.hibernate.SessionFactory;
import java.util.*;
import java.util.stream.IntStream;
import com.example.cineone_admin.repository.ProviderMappingRepository;
import com.example.cineone_admin.repository.RoomTypeCatalogRepository;
import jakarta.persistence.EntityManager;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:mapping;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.show-sql=false",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session.events.log=false"}, showSql = false)
@Import({ProviderMappingService.class, ProviderMappingTransactionService.class, RoomTypeCatalogService.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProviderMappingIntegrationTests {
    @Autowired private ProviderMappingService service;
    @Autowired private ProviderMappingRepository mappings;
    @Autowired private EntityManager em;
    @Autowired private RoomTypeCatalogService roomTypes;
    @Autowired private RoomTypeCatalogRepository catalog;
    @Autowired private PlatformTransactionManager transactionManager;
    private TransactionTemplate tx;
    private Long providerId;

    @BeforeEach void setup() {
        tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        roomTypes.registerTypes(List.of(RoomType.STANDARD, RoomType.IMAX, RoomType.FOUR_DX, RoomType.SCREENX));
        providerId = tx.execute(status -> {
            var provider = new DataProvider();
            provider.setCode(UUID.randomUUID().toString());
            provider.setName("Nhà cung cấp kiểm thử");
            provider.setProviderType("TEST");
            em.persist(provider);
            em.flush();
            return provider.getProviderId();
        });
    }

    private CinemaBrand brand() {
        var brand = new CinemaBrand();
        brand.setName("Hãng rạp kiểm thử " + UUID.randomUUID());
        return brand;
    }

    @Test void databaseGeneratesIdAndRepeatedSyncReusesIt() {
        Long id = service.resolveOrCreate(providerId, ProviderEntityType.CINEMA_BRAND, "brand-1", this::brand);
        assertNotNull(id);
        assertEquals(id, service.resolveOrCreate(providerId, ProviderEntityType.CINEMA_BRAND, "brand-1",
                () -> { throw new AssertionError("Phải dùng lại thực thể đã tồn tại"); }));
        assertEquals(id, service.findInternalId(providerId, ProviderEntityType.CINEMA_BRAND, "brand-1").orElseThrow());
        assertEquals("brand-1", service.findExternalId(providerId, ProviderEntityType.CINEMA_BRAND, id).orElseThrow());
    }

    @Test void outerFailureRollsBackEntityAndMappingTogether() {
        Long[] generated = new Long[1];
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            generated[0] = service.resolveOrCreate(providerId, ProviderEntityType.CINEMA_BRAND, "rollback", this::brand);
            throw new IllegalStateException("Giả lập lỗi ở bước đồng bộ tiếp theo");
        }));
        assertNotNull(generated[0]);
        assertTrue(service.findInternalId(providerId, ProviderEntityType.CINEMA_BRAND, "rollback").isEmpty());
        tx.executeWithoutResult(status -> assertNull(em.find(CinemaBrand.class, generated[0])));
    }

    @Test void databaseEnforcesBothUniqueConstraintsEvenOutsideService() {
        Long first = service.resolveOrCreate(providerId, ProviderEntityType.CINEMA_BRAND, "first", this::brand);
        Long second = tx.execute(status -> { var b = brand(); em.persist(b); em.flush(); return b.getBrandId(); });
        assertThrows(DataIntegrityViolationException.class, () -> tx.executeWithoutResult(status -> {
            var duplicate = new ProviderMapping();
            duplicate.setProvider(em.getReference(DataProvider.class, providerId));
            duplicate.setEntityType("CINEMA_BRAND");
            duplicate.setInternalId(first);
            duplicate.setExternalId("different-external");
            mappings.saveAndFlush(duplicate);
        }));
        assertThrows(DataIntegrityViolationException.class, () -> tx.executeWithoutResult(status -> {
            var duplicate = new ProviderMapping();
            duplicate.setProvider(em.getReference(DataProvider.class, providerId));
            duplicate.setEntityType("CINEMA_BRAND");
            duplicate.setInternalId(second);
            duplicate.setExternalId("first");
            mappings.saveAndFlush(duplicate);
        }));
    }

    @Test void simultaneousImportsRollbackLoserAndReuseWinner() throws Exception {
        var losingBrand = brand();
        var executor = Executors.newFixedThreadPool(2);
        var firstHasLock = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        try {
            Future<Long> first = executor.submit(() -> service.resolveOrCreate(providerId,
                    ProviderEntityType.CINEMA_BRAND, "concurrent", () -> {
                        firstHasLock.countDown();
                        try {
                            if (!releaseFirst.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Đã hết thời gian chờ");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                        return losingBrand;
                    }));
            assertTrue(firstHasLock.await(5, TimeUnit.SECONDS));
            Future<Long> second = executor.submit(() -> service.resolveOrCreate(providerId,
                    ProviderEntityType.CINEMA_BRAND, "concurrent", this::brand));
            Long winner = second.get(5, TimeUnit.SECONDS);
            releaseFirst.countDown();
            assertEquals(winner, first.get(10, TimeUnit.SECONDS));
            tx.executeWithoutResult(status -> {
                assertNotNull(em.find(CinemaBrand.class, winner));
                assertNotNull(losingBrand.getBrandId());
                assertNotEquals(winner, losingBrand.getBrandId());
                assertNull(em.find(CinemaBrand.class, losingBrand.getBrandId()),
                        "Thực thể được tạo trong giao dịch thua cuộc phải được hoàn tác");
            });
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }
    @Test void differentEntityTypesForSameProviderDoNotBlockEachOther() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var holding = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            Future<Long> first = executor.submit(() -> service.resolveOrCreate(providerId,
                    ProviderEntityType.CINEMA_BRAND, "holding", () -> {
                        holding.countDown();
                        try {
                            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Đã hết thời gian chờ");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                        return brand();
                    }));
            assertTrue(holding.await(5, TimeUnit.SECONDS));
            Future<Long> second = executor.submit(() -> service.resolveOrCreate(providerId,
                    ProviderEntityType.MOVIE, "movie", () -> {
                        var movie = new Movie();
                        movie.setTitle("Phim kiểm thử");
                        movie.setDuration(100);
                        return movie;
                    }));
            assertNotNull(second.get(5, TimeUnit.SECONDS));
            assertFalse(first.isDone());
            release.countDown();
            assertNotNull(first.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test void explicitStatusChangesPreserveSyncTimeAndCheckReactivationTarget() {
        Long id = service.resolveOrCreate(providerId, ProviderEntityType.CINEMA_BRAND, "status", this::brand);
        var initialTime = mappings.findByProviderProviderIdAndEntityTypeAndExternalId(
                providerId, "CINEMA_BRAND", "status").orElseThrow().getLastSyncedAt();
        service.changeStatus(providerId, ProviderEntityType.CINEMA_BRAND, "status", ProviderMappingStatus.INACTIVE);
        assertThrows(ProviderMappingException.class,
                () -> service.findInternalId(providerId, ProviderEntityType.CINEMA_BRAND, "status"));
        assertThrows(ProviderMappingException.class,
                () -> service.bind(providerId, ProviderEntityType.CINEMA_BRAND, "status", id));
        service.changeStatus(providerId, ProviderEntityType.CINEMA_BRAND, "status", ProviderMappingStatus.ERROR);
        service.changeStatus(providerId, ProviderEntityType.CINEMA_BRAND, "status", ProviderMappingStatus.ACTIVE);
        assertEquals(id, service.findInternalId(providerId, ProviderEntityType.CINEMA_BRAND, "status").orElseThrow());
        assertEquals(initialTime, mappings.findByProviderProviderIdAndEntityTypeAndExternalId(
                providerId, "CINEMA_BRAND", "status").orElseThrow().getLastSyncedAt());
        service.changeStatus(providerId, ProviderEntityType.CINEMA_BRAND, "status", ProviderMappingStatus.INACTIVE);
        tx.executeWithoutResult(status -> em.remove(em.find(CinemaBrand.class, id)));
        assertThrows(ProviderMappingException.class,
                () -> service.changeStatus(providerId, ProviderEntityType.CINEMA_BRAND, "status", ProviderMappingStatus.ACTIVE));
    }

    @Test void batchReusesExistingIdAndRollsBackOnLateFailure() {
        Long existing = service.resolveOrCreate(providerId, ProviderEntityType.CINEMA_BRAND, "existing", this::brand);
        var batch = service.resolveOrCreateBatch(providerId, ProviderEntityType.CINEMA_BRAND,
                List.of(new EntityCreationPair<>("existing", () -> { throw new AssertionError("Không được tạo lại"); }),
                        new EntityCreationPair<>("new", this::brand)));
        assertEquals(existing, batch.get("existing"));
        assertNotEquals(existing, batch.get("new"));
        assertThrows(RuntimeException.class, () -> service.resolveOrCreateBatch(providerId, ProviderEntityType.CINEMA_BRAND,
                List.of(new EntityCreationPair<>("valid", this::brand),
                        new EntityCreationPair<>("invalid", () -> new CinemaBrand()))));
        assertTrue(service.findInternalId(providerId, ProviderEntityType.CINEMA_BRAND, "valid").isEmpty());
    }

    @Test void bindBatchRejectsConflictsAndStatusBatchIsAtomic() {
        Long a = service.resolveOrCreate(providerId, ProviderEntityType.CINEMA_BRAND, "a", this::brand);
        Long b = service.resolveOrCreate(providerId, ProviderEntityType.CINEMA_BRAND, "b", this::brand);
        assertThrows(ProviderMappingException.class,
                () -> service.bindBatch(providerId, ProviderEntityType.CINEMA_BRAND, Map.of("a", b)));
        assertThrows(ProviderMappingException.class,
                () -> service.bindBatch(providerId, ProviderEntityType.CINEMA_BRAND, Map.of("other", a)));
        assertEquals(2, service.bindBatch(providerId, ProviderEntityType.CINEMA_BRAND, Map.of("a", a, "b", b)).size());
        assertThrows(ProviderMappingException.class, () -> service.changeStatusBatch(providerId,
                ProviderEntityType.CINEMA_BRAND, List.of("a", "missing"), ProviderMappingStatus.INACTIVE));
        assertEquals(a, service.findInternalId(providerId, ProviderEntityType.CINEMA_BRAND, "a").orElseThrow());
    }

    @Test void tenThousandMappingsUseBoundedQueriesAndDoNotLoadTargetEntities() {
        List<String> ids = IntStream.range(0, 10000).mapToObj(i -> "bulk-" + i).toList();
        for (int start = 0; start < ids.size(); start += ProviderMappingService.MAX_BATCH_SIZE) {
            var items = ids.subList(start, start + ProviderMappingService.MAX_BATCH_SIZE).stream()
                    .map(id -> new EntityCreationPair<>(id, this::brand)).toList();
            assertEquals(500, service.resolveOrCreateBatch(providerId, ProviderEntityType.CINEMA_BRAND, items).size());
        }
        var stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        assertEquals(10000, service.findInternalIds(providerId, ProviderEntityType.CINEMA_BRAND, ids).size());
        assertTrue(stats.getPrepareStatementCount() <= 40,
                "Tra cứu 10.000 ánh xạ chỉ cần tối đa 40 câu SQL theo lô");
        assertEquals(0, stats.getEntityStatistics(CinemaBrand.class.getName()).getLoadCount(),
                "Chỉ truy vấn cột ID của thực thể nội bộ");
        stats.clear();
        var repeats = ids.subList(0, 500).stream().map(id -> new EntityCreationPair<>(id,
                () -> { throw new AssertionError("Không được tạo lại thực thể"); })).toList();
        assertEquals(500, service.resolveOrCreateBatch(providerId, ProviderEntityType.CINEMA_BRAND, repeats).size());
        assertTrue(stats.getPrepareStatementCount() <= 6,
                "Đồng bộ lặp 500 ánh xạ không phát sinh truy vấn trên từng ID");
        assertEquals(0, stats.getEntityInsertCount());
    }
    @Test void seatEnumsAreStoredAsSchemaEnumValues() {
        Long id = tx.execute(status -> {
            var brand = brand(); em.persist(brand);
            var cinema = new Cinema(); cinema.setBrand(brand); cinema.setName("Rạp kiểm thử");
            cinema.setAddress("Địa chỉ kiểm thử"); em.persist(cinema);
            var room = new Room(); room.setCinema(cinema); room.setName("Phòng kiểm thử"); em.persist(room);
            var seat = new Seat(); seat.setRoom(room); seat.setSeatRow("A"); seat.setSeatNumber(1);
            seat.setSeatType(SeatType.SWEETBOX); seat.setStatus(SeatStatus.MAINTENANCE); em.persist(seat);
            em.flush(); return seat.getSeatId();
        });
        tx.executeWithoutResult(status -> {
            Object[] values = (Object[]) em.createNativeQuery("select seat_type, status from seats where seat_id = :id")
                    .setParameter("id", id).getSingleResult();
            assertArrayEquals(new Object[]{"SWEETBOX", "MAINTENANCE"}, values);
            assertEquals(SeatType.SWEETBOX, em.find(Seat.class, id).getSeatType());
        });
    }
    @ParameterizedTest
    @ValueSource(strings = {"CINEX", "SUPERPLEX", "VIP_PREMIUM", "FUTURE_ROOM_2030"})
    void sourceRoomTypesAreAutomaticallyAddedToCatalog(String code) {
        Long cinemaId = cinema();
        Long id = service.resolveOrCreate(providerId, ProviderEntityType.ROOM, "room-" + code,
                () -> room(cinemaId, code));
        assertTrue(catalog.existsById(code));
        tx.executeWithoutResult(status -> {
            var stored = em.find(Room.class, id);
            assertEquals(code, stored.getRoomType().getDatabaseValue());
            assertEquals(code, stored.getRoomTypeCatalog().getCode());
            assertEquals(code, em.createNativeQuery("select room_type from rooms where room_id = :id", String.class)
                    .setParameter("id", id).getSingleResult());
        });
    }

    @Test void noAuxiliaryMappingLockTableIsRequired() {
        tx.executeWithoutResult(status -> assertEquals(0L,
                em.createNativeQuery("select count(*) from information_schema.tables where upper(table_name) = 'PROVIDERMAPPINGLOCKS'", Long.class)
                        .getSingleResult()));
    }
    @Test void simultaneousNaturalKeyConflictReusesExactMappingWithoutMatchingByName() throws Exception {
        var losingBrand = brand();
        var winningBrand = brand();
        winningBrand.setName(losingBrand.getName());
        var executor = Executors.newFixedThreadPool(2);
        var waiting = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var loser = executor.submit(() -> service.resolveOrCreate(providerId, ProviderEntityType.CINEMA_BRAND,
                    "same-brand", () -> {
                        waiting.countDown();
                        try {
                            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Đã hết thời gian chờ");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt(); throw new IllegalStateException(e);
                        }
                        return losingBrand;
                    }));
            assertTrue(waiting.await(5, TimeUnit.SECONDS));
            var winner = executor.submit(() -> service.resolveOrCreate(providerId, ProviderEntityType.CINEMA_BRAND,
                    "same-brand", () -> winningBrand));
            Long id = winner.get(5, TimeUnit.SECONDS);
            release.countDown();
            assertEquals(id, loser.get(10, TimeUnit.SECONDS));
        } finally {
            release.countDown(); executor.shutdownNow();
        }
    }
    private Long cinema() {
        return tx.execute(status -> {
            var brand = brand(); em.persist(brand);
            var cinema = new Cinema(); cinema.setBrand(brand); cinema.setName("Rạp kiểm thử");
            cinema.setAddress("Địa chỉ kiểm thử"); em.persist(cinema); em.flush(); return cinema.getCinemaId();
        });
    }

    private Room room(Long cinemaId, String code) {
        var room = new Room();
        room.setCinema(em.getReference(Cinema.class, cinemaId));
        room.setName("Phòng " + UUID.randomUUID());
        room.setRoomType(RoomType.of(code));
        return room;
    }

    @Test void newCatalogEntryRollsBackWhenRoomCannotBeStored() {
        Long cinemaId = cinema();
        String code = "ROLLBACK_" + UUID.randomUUID();
        assertThrows(RuntimeException.class, () -> service.resolveOrCreate(providerId, ProviderEntityType.ROOM,
                "invalid-room", () -> { var room = room(cinemaId, code); room.setName(null); return room; }));
        assertFalse(catalog.existsById(code));
        assertTrue(service.findInternalId(providerId, ProviderEntityType.ROOM, "invalid-room").isEmpty());
    }

    @Test void updatingExistingRoomRegistersNewTypeBeforeFlushingForeignKey() {
        Long cinemaId = cinema();
        Long id = service.resolveOrCreate(providerId, ProviderEntityType.ROOM, "updated-room", () -> room(cinemaId, "STANDARD"));
        String code = "UPDATED_" + UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            em.find(Room.class, id).setRoomType(RoomType.of(code));
            service.bind(providerId, ProviderEntityType.ROOM, "updated-room", id);
        });
        assertTrue(catalog.existsById(code));
        tx.executeWithoutResult(status -> assertEquals(code, em.find(Room.class, id).getRoomTypeCatalog().getCode()));
    }

    @Test void repeatedCodesInRoomBatchUseOneCatalogEntry() {
        Long cinemaId = cinema();
        String code = "BATCH_" + UUID.randomUUID();
        var result = service.resolveOrCreateBatch(providerId, ProviderEntityType.ROOM,
                IntStream.range(0, 20).mapToObj(i -> new EntityCreationPair<>("room-" + i, () -> room(cinemaId, code))).toList());
        assertEquals(20, result.size());
        tx.executeWithoutResult(status -> assertEquals(1L, em.createQuery(
                "select count(t) from RoomTypeCatalog t where t.code = :code", Long.class).setParameter("code", code).getSingleResult()));
        roomTypes.registerTypes(List.of(RoomType.of(code), RoomType.of(code)));
        assertEquals(1, catalog.findAllByOrderByCodeAsc().stream().filter(t -> t.getCode().equals(code)).count());
    }

    @Test void concurrentRoomImportsShareNewCatalogCode() throws Exception {
        Long cinemaId = cinema();
        String code = "CONCURRENT_" + UUID.randomUUID();
        var ready = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var jobs = IntStream.range(0, 2).mapToObj(i -> executor.submit(() -> service.resolveOrCreate(
                    providerId, ProviderEntityType.ROOM, "parallel-room-" + i, () -> {
                        try { ready.await(5, TimeUnit.SECONDS); }
                        catch (Exception e) { throw new IllegalStateException("Không thể đồng bộ kiểm thử", e); }
                        return room(cinemaId, code);
                    }))).toList();
            assertNotEquals(jobs.get(0).get(10, TimeUnit.SECONDS), jobs.get(1).get(10, TimeUnit.SECONDS));
            assertTrue(catalog.existsById(code));
        } finally { executor.shutdownNow(); }
    }

    @Test void foreignKeyRejectsRoomWhoseTypeWasNotRegistered() {
        Long cinemaId = cinema();
        String code = "MISSING_" + UUID.randomUUID();
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(status -> {
            em.persist(room(cinemaId, code)); em.flush();
        }));
        assertFalse(catalog.existsById(code));
    }
}
