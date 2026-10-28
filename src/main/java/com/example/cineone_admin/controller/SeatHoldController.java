package com.example.cineone_admin.controller;

import com.example.cineone_admin.dto.common.ApiResponse;
import com.example.cineone_admin.dto.hold.HeldSeatDTO;
import com.example.cineone_admin.dto.hold.SeatHoldRequest;
import com.example.cineone_admin.dto.hold.SeatHoldResponse;
import com.example.cineone_admin.exception.BusinessException;
import com.example.cineone_admin.service.SeatHoldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/seats")
@RequiredArgsConstructor
public class SeatHoldController {

    private final SeatHoldService seatHoldService;

    @PostMapping("/hold")
    public ResponseEntity<ApiResponse<SeatHoldResponse>> holdSeats(
            @Valid @RequestBody SeatHoldRequest request,
            Authentication authentication) {
        
        // 1. Trích xuất userId từ token xác thực của server, chặn đứng giả mạo ID
        Long currentUserId = extractUserId(authentication);
        request.setUserId(currentUserId);

        SeatHoldResponse response = seatHoldService.holdSeats(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Giữ ghế thành công"));
    }

    @PostMapping("/release")
    public ResponseEntity<ApiResponse<Void>> releaseSeats(
            @RequestParam Long showtimeId,
            @RequestParam List<Long> seatIds,
            Authentication authentication) {
        
        // 2. Không nhận userId từ query param nữa mà lấy từ người đang đăng nhập
        Long currentUserId = extractUserId(authentication);
        
        seatHoldService.releaseSeats(showtimeId, currentUserId, seatIds);
        return ResponseEntity.ok(ApiResponse.success(null, "Nhả ghế thành công"));
    }

    // 3. Endpoint xem sơ đồ ghế giữ vẫn mở công khai để client tra cứu trạng thái
    @GetMapping("/held")
    public ResponseEntity<ApiResponse<List<HeldSeatDTO>>> getHeldSeats(@RequestParam Long showtimeId) {
        List<HeldSeatDTO> heldSeats = seatHoldService.getHeldSeats(showtimeId);
        return ResponseEntity.ok(ApiResponse.success(heldSeats, "Lấy danh sách ghế giữ thành công"));
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException("Yêu cầu đăng nhập để thực hiện thao tác giữ hoặc nhả ghế.");
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            throw new BusinessException("Định danh người dùng trong token không hợp lệ.");
        }
    }
}