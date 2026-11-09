package com.example.cineone_admin.controller.api;

import com.example.cineone_admin.dto.voucher.VoucherResponse;
import com.example.cineone_admin.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
public class VoucherApiController {

    private final VoucherService voucherService;

    /**
     * Lấy danh sách voucher trong Ví cá nhân theo tab: AVAILABLE, USED, EXPIRED
     */
    @GetMapping("/my-vouchers")
    public ResponseEntity<Map<String, Object>> getMyVouchers(
            @RequestParam(name = "tab", defaultValue = "AVAILABLE") String tab) {
        List<VoucherResponse> vouchers = voucherService.getMyVouchersMobileByTab(tab);
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("status", 200);
        body.put("message", "Lấy danh sách voucher thành công.");
        body.put("data", vouchers);
        return ResponseEntity.ok(body);
    }

    /**
     * Lấy chi tiết Voucher theo mã (cho màn hình Chi tiết voucher - Ảnh 4)
     */
    @GetMapping("/detail/{code}")
    public ResponseEntity<Map<String, Object>> getVoucherDetail(@PathVariable String code) {
        VoucherResponse detail = voucherService.getVoucherDetailByCode(code);
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("status", 200);
        body.put("message", "Lấy chi tiết voucher thành công.");
        body.put("data", detail);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> applyVoucher(@RequestBody Map<String, Object> request) {
        String code = (String) request.getOrDefault("code", "");
        Number subtotalNum = (Number) request.getOrDefault("subtotal", 0);
        long subtotal = subtotalNum.longValue();
        Map<String, Object> result = voucherService.applyVoucherForMobile(code, subtotal);
        Map<String, Object> body = new HashMap<>();
        body.put("success", result.get("success"));
        body.put("status", 200);
        body.put("message", result.get("message"));
        body.put("data", result);
        return ResponseEntity.ok(body);
    }
}