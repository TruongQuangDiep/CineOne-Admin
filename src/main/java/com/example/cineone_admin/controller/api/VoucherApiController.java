package com.example.cineone_admin.controller.api;

import com.example.cineone_admin.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
public class VoucherApiController {

    private final VoucherService voucherService;

    @GetMapping("/my-vouchers")
    public ResponseEntity<Map<String, Object>> getMyVouchers() {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("success", true);
        body.put("status", 200);
        body.put("message", "Lấy danh sách voucher thành công.");
        body.put("data", voucherService.getMyVouchersMobile());
        return ResponseEntity.ok(body);
    }
}