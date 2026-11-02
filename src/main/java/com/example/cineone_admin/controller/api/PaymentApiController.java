package com.example.cineone_admin.controller.api;

import com.example.cineone_admin.dto.common.ApiResponse;
import com.example.cineone_admin.dto.payment.PaymentLinkResponse;
import com.example.cineone_admin.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.payos.type.Webhook;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentApiController {

    private final PaymentService paymentService;

    @PostMapping("/create-link/{bookingId}")
    public ResponseEntity<ApiResponse<PaymentLinkResponse>> createPaymentLink(@PathVariable Long bookingId) {
        try {
            PaymentLinkResponse response = paymentService.createPaymentLink(bookingId);
            return ResponseEntity.ok(ApiResponse.<PaymentLinkResponse>builder()
                    .status(200)
                    .message("Tạo link thanh toán payOS thành công")
                    .data(response)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.<PaymentLinkResponse>builder()
                    .status(400)
                    .message("Không thể tạo link thanh toán: " + e.getMessage())
                    .data(null)
                    .build());
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> handleWebhook(@RequestBody Webhook webhookBody) {
        try {
            paymentService.processWebhook(webhookBody);
            return ResponseEntity.ok(Map.of("code", "00", "message", "Webhook processed successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("code", "01", "message", e.getMessage()));
        }
    }
}