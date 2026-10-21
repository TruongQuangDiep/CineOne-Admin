package com.example.cineone_admin.sync;

import com.example.cineone_admin.dto.sync.*;
import com.example.cineone_admin.dto.sync.NormalizedCatalogDTO.*;
import com.example.cineone_admin.entity.*;
import com.example.cineone_admin.enums.ProviderEntityType;
import com.example.cineone_admin.repository.DataProviderRepository;
import com.example.cineone_admin.service.ProviderMappingService;
import com.example.cineone_admin.service.RoomTypeCatalogService;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Each source record and its mapping commit together in an independent transaction. */
@Service
public class SyncRecordStore {
    private final EntityManager em;
    private final ProviderMappingService mappings;
    private final DataProviderRepository providers;
    private final RoomTypeCatalogService roomTypes;
    public SyncRecordStore(EntityManager em, ProviderMappingService mappings,
            DataProviderRepository providers, RoomTypeCatalogService roomTypes) {
        this.em = em; this.mappings = mappings; this.providers = providers; this.roomTypes = roomTypes;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void save(Long providerId, ProviderEntityType type, String externalId, Object source) {
        // Serialize writes for a provider across JVMs, while keeping each record independent.
        var provider = em.find(DataProvider.class, providerId, LockModeType.PESSIMISTIC_WRITE);
        if (provider == null || !"ACTIVE".equals(provider.getStatus()))
            throw new IllegalArgumentException("Nhà cung cấp không ở trạng thái ACTIVE");
        Long existing = mappings.findInternalId(providerId, type, externalId).orElse(null);
        Object target = existing == null ? create(type) : em.find(type.getEntityClass(), existing, LockModeType.PESSIMISTIC_WRITE);
        switch (source) {
            case CinemaBrandDTO s -> {
                var t = (CinemaBrand) target;
                t.setName(s.name()); t.setLogoUrl(s.logoUrl());
                if (existing == null) t.setStatus(s.status().name());
            }
            case CinemaDTO s -> {
                var t = (Cinema) target;
                var brand = reference(s.brand(), CinemaBrand.class);
                if (existing != null && !t.getBrand().getBrandId().equals(brand.getBrandId()))
                    throw new IllegalArgumentException("Không chuyển rạp đã tồn tại sang hãng khác");
                t.setBrand(brand); t.setName(s.name()); t.setAddress(s.address());
                t.setLatitude(s.latitude()); t.setLongitude(s.longitude()); t.setPhone(s.phone());
                if (existing == null) t.setStatus(s.status().name());
            }
            case RoomDTO s -> {
                var t = (Room) target;
                var cinema = reference(s.cinema(), Cinema.class);
                if (existing != null && (!t.getCinema().getCinemaId().equals(cinema.getCinemaId())
                        || !t.getRoomType().equals(s.roomType())))
                    throw new IllegalArgumentException("Xung đột cấu trúc phòng đã tồn tại");
                roomTypes.registerTypes(List.of(s.roomType()));
                t.setCinema(cinema); t.setName(s.name()); t.setRoomType(s.roomType());
                if (existing == null) t.setStatus(s.status().name());
            }
            case SeatDTO s -> {
                var t = (Seat) target;
                var room = reference(s.room(), Room.class);
                if (existing != null && (!t.getRoom().getRoomId().equals(room.getRoomId())
                        || !t.getSeatRow().equals(s.seatRow()) || !t.getSeatNumber().equals(s.seatNumber())
                        || t.getSeatType() != s.seatType()))
                    throw new IllegalArgumentException("Xung đột cấu trúc ghế đã tồn tại");
                t.setRoom(room); t.setSeatRow(s.seatRow()); t.setSeatNumber(s.seatNumber()); t.setSeatType(s.seatType());
                if (existing == null) t.setStatus(s.status());
            }
            case FnbDTO s -> {
                var t = (FnbItem) target;
                var cinema = reference(s.cinema(), Cinema.class);
                var brand = reference(s.brand(), CinemaBrand.class);
                if (!cinema.getBrand().getBrandId().equals(brand.getBrandId()))
                    throw new IllegalArgumentException("F&B có hãng không khớp rạp");
                t.setBrand(brand); t.setCinema(cinema); t.setName(s.name()); t.setDescription(s.description());
                t.setPrice(s.price()); t.setImageUrl(s.imageUrl());
                if (existing == null) t.setStatus(s.status().name());
            }
            case ShowtimeDTO s -> {
                var t = (Showtime) target;
                var movie = reference(s.movie(), Movie.class);
                var room = reference(s.room(), Room.class);
                if (existing != null) {
                    if (!t.getMovie().getMovieId().equals(movie.getMovieId()) || !t.getRoom().getRoomId().equals(room.getRoomId())
                            || !t.getStartTime().equals(s.startTime()) || !t.getEndTime().equals(s.endTime())
                            || t.getBasePrice().compareTo(s.basePrice()) != 0)
                        throw new IllegalArgumentException("Xung đột suất chiếu: giữ nguyên phim, phòng, giờ và giá đã lưu");
                    // Preserve admin state (CLOSED/CANCELLED/ENDED) on repeated sync too.
                } else {
                    em.lock(room, LockModeType.PESSIMISTIC_WRITE);
                    Long overlaps = em.createQuery("select count(s) from Showtime s where s.room.roomId = :room "
                            + "and s.status <> 'CANCELLED' and s.startTime < :end and s.endTime > :start", Long.class)
                            .setParameter("room", room.getRoomId()).setParameter("start", s.startTime())
                            .setParameter("end", s.endTime()).getSingleResult();
                    if (overlaps > 0) throw new IllegalArgumentException("Suất chiếu trùng lịch phòng");
                    t.setMovie(movie); t.setRoom(room); t.setStartTime(s.startTime()); t.setEndTime(s.endTime());
                    t.setBasePrice(s.basePrice()); t.setStatus(s.status().name());
                }
            }
            default -> throw new IllegalArgumentException("Loại dữ liệu không được hỗ trợ trong đồng bộ hãng rạp");
        }
        if (existing == null) em.persist(target);
        em.flush();
        Long id = (Long) em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(target);
        mappings.bind(providerId, type, externalId, id);
        em.flush();
    }

    private <T> T reference(ProviderReferenceDTO ref, Class<T> entityClass) {
        var provider = providers.findByCode(ref.providerCode()).orElseThrow(
                () -> new IllegalArgumentException("Chưa cấu hình nguồn tham chiếu " + ref.providerCode()));
        if (!"ACTIVE".equals(provider.getStatus())) throw new IllegalArgumentException("Nguồn tham chiếu không ACTIVE");
        Long id = mappings.findInternalId(provider.getProviderId(), ref.entityType(), ref.externalId())
                .orElseThrow(() -> new IllegalArgumentException("Thiếu dữ liệu tham chiếu " + ref.entityType() + ": " + ref.externalId()));
        return em.getReference(entityClass, id);
    }

    private Object create(ProviderEntityType type) {
        return switch (type) {
            case CINEMA_BRAND -> new CinemaBrand(); case CINEMA -> new Cinema(); case ROOM -> new Room();
            case SEAT -> new Seat(); case FNB_ITEM -> new FnbItem(); case SHOWTIME -> new Showtime();
            default -> throw new IllegalArgumentException("Loại dữ liệu không được hỗ trợ");
        };
    }
}
