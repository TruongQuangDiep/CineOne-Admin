package com.example.cineone_admin.controller;

import com.example.cineone_admin.dto.common.ApiResponse;
import com.example.cineone_admin.dto.hold.HeldSeatDTO;
import com.example.cineone_admin.dto.hold.SeatHoldRequest;
import com.example.cineone_admin.dto.hold.SeatHoldResponse;
import com.example.cineone_admin.service.SeatHoldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/seats")
@RequiredArgsConstructor
public class SeatHoldController {

    private final SeatHoldService seatHoldService;

    @PostMapping("/hold")
    public ResponseEntity<ApiResponse<SeatHoldResponse>> holdSeats(@Valid @RequestBody SeatHoldRequest request) {
        SeatHoldResponse response = seatHoldService.holdSeats(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Giữ ghế thành công"));
    }

    @PostMapping("/release")
    public ResponseEntity<ApiResponse<Void>> releaseSeats(
            @RequestParam Long showtimeId,
            @RequestParam List<Long> seatIds,
            @RequestParam Long userId) {
        seatHoldService.releaseSeats(showtimeId, userId, seatIds);
        return ResponseEntity.ok(ApiResponse.success(null, "Nhả ghế thành công"));
    }

    @GetMapping("/held")
    public ResponseEntity<ApiResponse<List<HeldSeatDTO>>> getHeldSeats(@RequestParam Long showtimeId) {
        List<HeldSeatDTO> heldSeats = seatHoldService.getHeldSeats(showtimeId);
        return ResponseEntity.ok(ApiResponse.success(heldSeats, "Lấy danh sách ghế giữ thành công"));
    }
}