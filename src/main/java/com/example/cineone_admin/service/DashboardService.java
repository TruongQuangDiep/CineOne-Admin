package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.dashboard.DashboardSummaryDTO;
import com.example.cineone_admin.dto.dashboard.DashboardSummaryDTO.ProviderSyncStatusDTO;
import com.example.cineone_admin.dto.dashboard.DashboardSummaryDTO.SyncAlertDTO;
import com.example.cineone_admin.dto.dashboard.DashboardSummaryDTO.SyncActivityDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashboardService {

    public DashboardSummaryDTO getDashboardSummary() {
        // 1. Số liệu thống kê sơ bộ lấy chuẩn theo thiết kế Wireframe
        long totalMovies = 128;
        long totalCinemas = 12;
        long totalShowtimes = 356;
        long totalUsers = 12540;

        // 2. Trạng thái đồng bộ đối tác rạp
        List<ProviderSyncStatusDTO> syncStatuses = List.of(
                new ProviderSyncStatusDTO("Provider A (CGV)", "CGV", "A", "Thành công", "text-success"),
                new ProviderSyncStatusDTO("Provider B (Lotte)", "LOTTE", "B", "Thành công", "text-success"),
                new ProviderSyncStatusDTO("Provider C (Galaxy)", "GALAXY", "C", "Đang xử lý", "text-primary")
        );

        // 3. Khối cảnh báo
        List<SyncAlertDTO> alerts = List.of(
                new SyncAlertDTO("Provider B", "LOTTE", 5, "5 mapping lỗi", "/mappings")
        );

        // 4. Tiến độ hoạt động đồng bộ (%)
        List<SyncActivityDTO> activities = List.of(
                new SyncActivityDTO("Provider A", "CGV", "A", 98),
                new SyncActivityDTO("Provider B", "LOTTE", "B", 92),
                new SyncActivityDTO("Provider C", "GALAXY", "C", 97),
                new SyncActivityDTO("Provider D", "BETA", "D", 96)
        );

        return new DashboardSummaryDTO(totalMovies, totalCinemas, totalShowtimes, totalUsers, syncStatuses, alerts, activities);
    }
}