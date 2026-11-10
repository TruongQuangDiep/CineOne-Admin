package com.example.cineone_admin.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record DashboardSummaryDTO(
        long totalMovies,
        long totalCinemas,
        long totalShowtimes,
        long totalUsers,
        List<ProviderSyncStatusDTO> syncStatuses,
        List<SyncAlertDTO> alerts,
        List<SyncActivityDTO> activities,
        BigDecimal totalRevenue,
        long totalTicketsSold,
        double occupancyRate,
        List<String> revenueDates,
        List<BigDecimal> revenueValues,
        List<OccupancyByCinemaDTO> occupancyByCinemas,
        List<SyncPerformanceReportDTO> syncPerformanceReports,
        int totalSyncSuccess,
        int totalSyncFailed
) {
    public DashboardSummaryDTO(
            long totalMovies,
            long totalCinemas,
            long totalShowtimes,
            long totalUsers,
            List<ProviderSyncStatusDTO> syncStatuses,
            List<SyncAlertDTO> alerts,
            List<SyncActivityDTO> activities
    ) {
        this(
                totalMovies, totalCinemas, totalShowtimes, totalUsers,
                syncStatuses, alerts, activities,
                BigDecimal.ZERO, 0L, 0.0,
                List.of(), List.of(), List.of(), List.of(), 0, 0
        );
    }

    public record ProviderSyncStatusDTO(
            String name,
            String code,
            String avatarLetter,
            String statusText,
            String badgeClass
    ) {}

    public record SyncAlertDTO(
            String providerName,
            String providerCode,
            int errorCount,
            String message,
            String detailUrl
    ) {}

    public record SyncActivityDTO(
            String name,
            String code,
            String avatarLetter,
            int percentage
    ) {}

    public record OccupancyByCinemaDTO(
            String cinemaName,
            String cinemaBrand,
            int totalRooms,
            int totalSeats,
            int bookedSeats,
            double occupancyRate
    ) {}

    public record SyncPerformanceReportDTO(
            String providerName,
            String providerCode,
            String avatarLetter,
            int totalSyncCount,
            int successCount,
            int failedCount,
            double successRate,
            String lastSyncTime,
            String statusText,
            String badgeClass
    ) {}
}