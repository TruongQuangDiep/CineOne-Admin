package com.example.cineone_admin.controller.api;

import com.example.cineone_admin.dto.common.ApiResponse;
import com.example.cineone_admin.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingApiController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createBooking(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        Map<String, Object> bookingResult = bookingService.createBookingFromMobile(request, authentication);
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .status(200)
                .message("Tạo đơn đặt vé thành công")
                .data(bookingResult)
                .build());
    }
}