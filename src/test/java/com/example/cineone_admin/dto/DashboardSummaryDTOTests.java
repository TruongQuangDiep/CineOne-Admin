package com.example.cineone_admin.dto;

import com.example.cineone_admin.dto.dashboard.DashboardSummaryDTO;
import com.example.cineone_admin.dto.dashboard.DashboardSummaryDTO.ProviderSyncStatusDTO;
import com.example.cineone_admin.dto.dashboard.DashboardSummaryDTO.SyncAlertDTO;
import com.example.cineone_admin.dto.dashboard.DashboardSummaryDTO.SyncActivityDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DashboardSummaryDTOTests {

    @Test
    @DisplayName("DashboardSummaryDTO lưu trữ chính xác các chỉ số tổng quan và danh sách con")
    void shouldStoreSummaryValuesCorrectly() {
        var statuses = List.of(new ProviderSyncStatusDTO("CGV Cinema", "CGV", "C", "Thành công", "text-success"));
        var alerts = List.of(new SyncAlertDTO("Lotte Cinema", "LOTTE", 3, "3 lỗi mapping", "/mappings"));
        var activities = List.of(new SyncActivityDTO("Galaxy Cinema", "GALAXY", "G", 95));

        var dto = new DashboardSummaryDTO(150, 20, 500, 10000, statuses, alerts, activities);

        assertEquals(150, dto.totalMovies());
        assertEquals(20, dto.totalCinemas());
        assertEquals(500, dto.totalShowtimes());
        assertEquals(10000, dto.totalUsers());
        assertEquals(1, dto.syncStatuses().size());
        assertEquals(1, dto.alerts().size());
        assertEquals(1, dto.activities().size());
    }

    @Test void shouldHoldCorrectDetailsInSubRecords() {
        var status = new ProviderSyncStatusDTO("Beta Cinemas", "BETA", "B", "Đang xử lý", "text-primary");
        assertEquals("Beta Cinemas", status.name());
        assertEquals("BETA", status.code());
        assertEquals("B", status.avatarLetter());
        assertEquals("Đang xử lý", status.statusText());
        assertEquals("text-primary", status.badgeClass());

        var alert = new SyncAlertDTO("CGV", "CGV", 5, "5 mapping lỗi", "/mappings");
        assertEquals("CGV", alert.providerName());
        assertEquals(5, alert.errorCount());
        assertEquals("/mappings", alert.detailUrl());

        var activity = new SyncActivityDTO("Lotte", "LOTTE", "L", 88);
        assertEquals(88, activity.percentage());
        assertTrue(activity.percentage() >= 0 && activity.percentage() <= 100);
    }

    @Test
    @DisplayName("Hai đối tượng DashboardSummaryDTO cùng dữ liệu phải bằng nhau (Record Equality)")
    void recordEqualityCheck() {
        var dto1 = new DashboardSummaryDTO(10, 5, 20, 100, List.of(), List.of(), List.of());
        var dto2 = new DashboardSummaryDTO(10, 5, 20, 100, List.of(), List.of(), List.of());

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }
}