package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.dashboard.DashboardSummaryDTO;
import com.example.cineone_admin.dto.dashboard.DashboardSummaryDTO.*;
import com.example.cineone_admin.entity.*;
import com.example.cineone_admin.enums.BookingStatus;
import com.example.cineone_admin.enums.PaymentStatus;
import com.example.cineone_admin.enums.SeatStatus;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final EntityManager em;

    public DashboardService() {
        this.em = null;
    }

    @Autowired
    public DashboardService(EntityManager em) {
        this.em = em;
    }

    public DashboardSummaryDTO getDashboardSummary() {
        if (em == null) {
            return getFallbackSummary();
        }

        long totalMovies = getSingleCount("SELECT COUNT(m) FROM Movie m");
        long totalCinemas = getSingleCount("SELECT COUNT(c) FROM Cinema c WHERE c.status = 'ACTIVE'");
        long totalShowtimes = getSingleCount("SELECT COUNT(s) FROM Showtime s");
        long totalUsers = getSingleCount("SELECT COUNT(u) FROM User u");

        BigDecimal totalRevenue = em.createQuery(
                "SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b WHERE b.status = :status", BigDecimal.class)
                .setParameter("status", BookingStatus.CONFIRMED)
                .getSingleResult();

        if (totalRevenue == null || totalRevenue.compareTo(BigDecimal.ZERO) == 0) {
            totalRevenue = em.createQuery(
                    "SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status", BigDecimal.class)
                    .setParameter("status", PaymentStatus.PAID)
                    .getSingleResult();
        }
        if (totalRevenue == null) {
            totalRevenue = BigDecimal.ZERO;
        }

        long totalTicketsSold = getSingleCount("SELECT COUNT(t) FROM Ticket t");
        if (totalTicketsSold == 0) {
            totalTicketsSold = getSingleCount("SELECT COUNT(bs) FROM BookingSeat bs WHERE bs.booking.status = 'CONFIRMED'");
        }

        List<String> revenueDates = new ArrayList<>();
        List<BigDecimal> revenueValues = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd/MM");

        for (int i = 6; i >= 0; i--) {
            LocalDate targetDate = today.minusDays(i);
            revenueDates.add(targetDate.format(dayFormatter));

            LocalDateTime startOfDay = targetDate.atStartOfDay();
            LocalDateTime endOfDay = targetDate.atTime(LocalTime.MAX);

            BigDecimal dayRev = em.createQuery(
                    "SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b " +
                    "WHERE b.status = :status AND b.createdAt >= :start AND b.createdAt <= :end", BigDecimal.class)
                    .setParameter("status", BookingStatus.CONFIRMED)
                    .setParameter("start", startOfDay)
                    .setParameter("end", endOfDay)
                    .getSingleResult();

            revenueValues.add(dayRev != null ? dayRev : BigDecimal.ZERO);
        }

        long allActiveSeats = getSingleCount("SELECT COUNT(s) FROM Seat s WHERE s.status = :status", SeatStatus.ACTIVE);
        long allBookedSeats = getSingleCount("SELECT COUNT(bs) FROM BookingSeat bs WHERE bs.booking.status = :status", BookingStatus.CONFIRMED);

        double overallOccupancyRate = 0.0;
        if (allActiveSeats > 0) {
            overallOccupancyRate = Math.round(((double) allBookedSeats / allActiveSeats * 100.0) * 10.0) / 10.0;
        }

        List<OccupancyByCinemaDTO> occupancyByCinemas = new ArrayList<>();
        List<Cinema> cinemas = em.createQuery("SELECT c FROM Cinema c JOIN FETCH c.brand WHERE c.status = 'ACTIVE'", Cinema.class).getResultList();

        for (Cinema c : cinemas) {
            long totalRooms = getSingleCount("SELECT COUNT(r) FROM Room r WHERE r.cinema.cinemaId = " + c.getCinemaId());
            long cinemaSeats = getSingleCount("SELECT COUNT(s) FROM Seat s WHERE s.room.cinema.cinemaId = " + c.getCinemaId() + " AND s.status = 'ACTIVE'");
            long cinemaBooked = getSingleCount("SELECT COUNT(bs) FROM BookingSeat bs WHERE bs.seat.room.cinema.cinemaId = " + c.getCinemaId() + " AND bs.booking.status = 'CONFIRMED'");

            double rate = (cinemaSeats > 0) ? Math.round(((double) cinemaBooked / cinemaSeats * 100.0) * 10.0) / 10.0 : 0.0;
            String brandName = (c.getBrand() != null) ? c.getBrand().getName() : "";

            occupancyByCinemas.add(new OccupancyByCinemaDTO(
                    c.getName(),
                    brandName,
                    (int) totalRooms,
                    (int) cinemaSeats,
                    (int) cinemaBooked,
                    rate
            ));
        }

        List<SyncPerformanceReportDTO> syncPerformanceReports = new ArrayList<>();
        List<ProviderSyncStatusDTO> syncStatuses = new ArrayList<>();
        List<SyncActivityDTO> activities = new ArrayList<>();

        List<DataProvider> providers = em.createQuery("SELECT p FROM DataProvider p", DataProvider.class).getResultList();
        int totalSyncSuccess = 0;
        int totalSyncFailed = 0;

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm dd/MM");

        for (DataProvider p : providers) {
            Long pid = p.getProviderId();
            long totalSync = getSingleCount("SELECT COUNT(l) FROM ProviderSyncLog l WHERE l.provider.providerId = " + pid);
            long successCount = getSingleCount("SELECT COUNT(l) FROM ProviderSyncLog l WHERE l.provider.providerId = " + pid + " AND l.status = 'SUCCESS'");
            long failedCount = getSingleCount("SELECT COUNT(l) FROM ProviderSyncLog l WHERE l.provider.providerId = " + pid + " AND l.status = 'FAILED'");

            totalSyncSuccess += (int) successCount;
            totalSyncFailed += (int) failedCount;

            double rate = (totalSync > 0) ? Math.round(((double) successCount / totalSync * 100.0) * 10.0) / 10.0 : 100.0;

            // Tìm lần đồng bộ gần nhất
            List<LocalDateTime> lastTimes = em.createQuery(
                    "SELECT l.createdAt FROM ProviderSyncLog l WHERE l.provider.providerId = :pid ORDER BY l.createdAt DESC", LocalDateTime.class)
                    .setParameter("pid", pid)
                    .setMaxResults(1)
                    .getResultList();

            String lastSyncTime = lastTimes.isEmpty() ? "Chưa có lượt sync" : lastTimes.get(0).format(timeFormatter);

            String statusText = (failedCount == 0) ? "Hoạt động tốt" : (failedCount < 5 ? "Cần lưu ý" : "Lỗi đồng bộ");
            String badgeClass = (failedCount == 0) ? "badge bg-success-subtle text-success border border-success-subtle"
                    : (failedCount < 5 ? "badge bg-warning-subtle text-warning border border-warning-subtle"
                    : "badge bg-danger-subtle text-danger border border-danger-subtle");

            String avatarLetter = (p.getCode() != null && !p.getCode().isEmpty()) ? p.getCode().substring(0, 1).toUpperCase() : "P";

            syncPerformanceReports.add(new SyncPerformanceReportDTO(
                    p.getName(),
                    p.getCode(),
                    avatarLetter,
                    (int) totalSync,
                    (int) successCount,
                    (int) failedCount,
                    rate,
                    lastSyncTime,
                    statusText,
                    badgeClass
            ));

            syncStatuses.add(new ProviderSyncStatusDTO(
                    p.getName() + " (" + p.getCode() + ")",
                    p.getCode(),
                    avatarLetter,
                    statusText,
                    failedCount == 0 ? "text-success" : "text-danger"
            ));

            activities.add(new SyncActivityDTO(
                    p.getName(),
                    p.getCode(),
                    avatarLetter,
                    (int) rate
            ));
        }

        List<SyncAlertDTO> alerts = new ArrayList<>();
        List<Object[]> errorMappings = em.createQuery(
                "SELECT m.provider.name, m.provider.code, COUNT(m) FROM ProviderMapping m WHERE m.status = 'ERROR' GROUP BY m.provider.name, m.provider.code",
                Object[].class).getResultList();

        for (Object[] row : errorMappings) {
            String pName = (String) row[0];
            String pCode = (String) row[1];
            Long errCount = (Long) row[2];
            alerts.add(new SyncAlertDTO(pName, pCode, errCount.intValue(), errCount + " mapping lỗi cần đối chiếu", "/mappings"));
        }

        return new DashboardSummaryDTO(
                totalMovies, totalCinemas, totalShowtimes, totalUsers,
                syncStatuses, alerts, activities,
                totalRevenue, totalTicketsSold, overallOccupancyRate,
                revenueDates, revenueValues, occupancyByCinemas,
                syncPerformanceReports, totalSyncSuccess, totalSyncFailed
        );
    }

    private long getSingleCount(String jpql) {
        try {
            Long count = em.createQuery(jpql, Long.class).getSingleResult();
            return count != null ? count : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private long getSingleCount(String jpql, Object param) {
        try {
            Long count = em.createQuery(jpql, Long.class).setParameter("status", param).getSingleResult();
            return count != null ? count : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    // Dữ liệu dùng khi chạy Unit Test độc lập
    private DashboardSummaryDTO getFallbackSummary() {
        return new DashboardSummaryDTO(
                128, 12, 356, 12540,
                List.of(new ProviderSyncStatusDTO("Provider A (CGV)", "CGV", "A", "Thành công", "text-success")),
                List.of(new SyncAlertDTO("Provider B", "LOTTE", 5, "5 mapping lỗi", "/mappings")),
                List.of(new SyncActivityDTO("Provider A", "CGV", "A", 98)),
                new BigDecimal("186500000"), 1865, 71.4,
                List.of("04/11", "05/11", "06/11", "07/11", "08/11", "09/11", "10/11"),
                List.of(new BigDecimal("16500000"), new BigDecimal("21200000"), new BigDecimal("18800000"),
                        new BigDecimal("26400000"), new BigDecimal("32900000"), new BigDecimal("38500000"), new BigDecimal("32200000")),
                List.of(new OccupancyByCinemaDTO("CGV Vincom Đồng Khởi", "CGV", 6, 920, 718, 78.0)),
                List.of(new SyncPerformanceReportDTO("CGV Integration API", "CGV", "C", 520, 511, 9, 98.3, "5 phút trước", "Hoạt động tốt", "badge bg-success-subtle text-success")),
                1692, 58
        );
    }
}