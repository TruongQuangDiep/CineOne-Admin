package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.dashboard.DashboardSummaryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DashboardServiceTests {

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService();
    }

    @Test void shouldReturnNonNullSummary() {
        DashboardSummaryDTO summary = dashboardService.getDashboardSummary();
        assertNotNull(summary, "Dữ liệu tổng quan Dashboard không được null");
    }

    @Test void shouldContainValidKpiMetrics() {
        DashboardSummaryDTO summary = dashboardService.getDashboardSummary();

        assertTrue(summary.totalMovies() > 0, "Số lượng phim phải > 0");
        assertTrue(summary.totalCinemas() > 0, "Số lượng rạp phải > 0");
        assertTrue(summary.totalShowtimes() > 0, "Số lượng suất chiếu phải > 0");
        assertTrue(summary.totalUsers() > 0, "Số lượng người dùng phải > 0");
    }

    @Test void shouldContainValidSyncStatusesAndActivities() {
        DashboardSummaryDTO summary = dashboardService.getDashboardSummary();

        assertNotNull(summary.syncStatuses());
        assertFalse(summary.syncStatuses().isEmpty(), "Phải có danh sách trạng thái đồng bộ");

        assertNotNull(summary.activities());
        assertFalse(summary.activities().isEmpty(), "Phải có danh sách tiến độ đồng bộ");

        // Kiểm tra phần trăm tiến độ phải nằm trong khoảng từ 0% đến 100%
        summary.activities().forEach(activity -> {
            assertTrue(activity.percentage() >= 0 && activity.percentage() <= 100,
                    "Phần trăm đồng bộ của " + activity.name() + " phải từ 0 đến 100");
        });
    }

    @Test void shouldContainAlertsWithValidDetailUrl() {
        DashboardSummaryDTO summary = dashboardService.getDashboardSummary();

        assertNotNull(summary.alerts());
        assertFalse(summary.alerts().isEmpty(), "Phải có ít nhất 1 cảnh báo lỗi mapping sơ bộ");
        summary.alerts().forEach(alert -> {
            assertTrue(alert.errorCount() > 0, "Số lỗi mapping phải lớn hơn 0");
            assertEquals("/mappings", alert.detailUrl(), "Đường dẫn chi tiết phải trỏ về trang /mappings");
        });
    }
}