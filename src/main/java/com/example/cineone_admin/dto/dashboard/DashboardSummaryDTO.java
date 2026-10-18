package com.example.cineone_admin.dto.dashboard;

import java.util.List;

public record DashboardSummaryDTO(
        long totalMovies,
        long totalCinemas,
        long totalShowtimes,
        long totalUsers,
        List<ProviderSyncStatusDTO> syncStatuses,
        List<SyncAlertDTO> alerts,
        List<SyncActivityDTO> activities
) {
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
}