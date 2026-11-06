package com.example.cineone_admin.controller.api;

import com.example.cineone_admin.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

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

    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> applyVoucher(@RequestBody Map<String, Object> request) {
        String code = (String) request.getOrDefault("code", "");
        Number subtotalNum = (Number) request.getOrDefault("subtotal", 0);
        long subtotal = subtotalNum.longValue();
        Map<String, Object> result = voucherService.applyVoucherForMobile(code, subtotal);
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("success", result.get("success"));
        body.put("status", 200);
        body.put("message", result.get("message"));
        body.put("data", result);
        return ResponseEntity.ok(body);
    }
}