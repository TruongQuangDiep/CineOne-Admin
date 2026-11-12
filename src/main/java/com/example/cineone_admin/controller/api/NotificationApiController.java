package com.example.cineone_admin.controller.api;

import com.example.cineone_admin.dto.common.ApiResponse;
import com.example.cineone_admin.dto.notification.UserNotificationResponse;
import com.example.cineone_admin.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationApiController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserNotificationResponse>>> getMyNotifications(
            @RequestParam(required = false) String filter,
            Authentication authentication) {
        List<UserNotificationResponse> data = notificationService.getMyNotifications(authentication, filter);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách thông báo thành công."));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(Authentication authentication) {
        long unreadCount = notificationService.getUnreadCount(authentication);
        return ResponseEntity.ok(ApiResponse.success(Map.of("unreadCount", unreadCount), "Lấy số thông báo chưa đọc thành công."));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<UserNotificationResponse>> markAsRead(
            @PathVariable Long id,
            Authentication authentication) {
        UserNotificationResponse data = notificationService.markAsRead(id, authentication);
        return ResponseEntity.ok(ApiResponse.success(data, "Đã đánh dấu thông báo đã đọc."));
    }

    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead(Authentication authentication) {
        int updated = notificationService.markAllAsRead(authentication);
        return ResponseEntity.ok(ApiResponse.success(Map.of("updatedCount", updated), "Đã đánh dấu tất cả thông báo đã đọc."));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserNotificationResponse>> getNotificationDetail(
            @PathVariable Long id,
            Authentication authentication) {
        UserNotificationResponse data = notificationService.getMyNotificationDetail(id, authentication);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy chi tiết thông báo thành công."));
    }
}