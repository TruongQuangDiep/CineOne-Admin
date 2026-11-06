package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.payment.PaymentLinkResponse;
import com.example.cineone_admin.dto.payment.PaymentResponse;
import com.example.cineone_admin.entity.Booking;
import com.example.cineone_admin.entity.Payment;
import com.example.cineone_admin.enums.BookingStatus;
import com.example.cineone_admin.enums.PaymentStatus;
import com.example.cineone_admin.exception.ResourceNotFoundException;
import com.example.cineone_admin.repository.BookingRepository;
import com.example.cineone_admin.repository.PaymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.payos.PayOS;
import vn.payos.type.Webhook;
import vn.payos.type.WebhookData;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final PayOS payOS;

    @Value("${payos.client-id}")
    private String clientId;

    @Value("${payos.api-key}")
    private String apiKey;

    @Value("${payos.checksum-key}")
    private String checksumKey;

    @Value("${payos.return-url}")
    private String returnUrl;

    @Value("${payos.cancel-url}")
    private String cancelUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPayments(String keyword, String status, Pageable pageable) {
        PaymentStatus parsedStatus = parseStatus(status);
        return paymentRepository.searchPayments(keyword, parsedStatus, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Payment getPaymentEntity(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch thanh toán ID: " + id));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        return mapToResponse(getPaymentEntity(id));
    }

    @Transactional
    public void updatePaymentStatus(Long id, PaymentStatus status) {
        Payment payment = getPaymentEntity(id);
        if (status != null) {
            payment.setStatus(status);
        }
        paymentRepository.save(payment);
    }

    public PaymentResponse mapToResponse(Payment payment) {
        PaymentResponse res = new PaymentResponse();
        res.setPaymentId(payment.getPaymentId());
        if (payment.getBooking() != null) {
            res.setBookingId(payment.getBooking().getBookingId());
        }
        res.setProvider(payment.getProvider());
        res.setOrderCode(payment.getOrderCode());
        res.setAmount(payment.getAmount());
        if (payment.getStatus() != null) {
            res.setStatus(payment.getStatus());
        }
        res.setPaymentUrl(payment.getPaymentUrl());
        res.setPaidAt(payment.getPaidAt());
        res.setCreatedAt(payment.getCreatedAt());
        return res;
    }

    private PaymentStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return PaymentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Transactional
    public PaymentLinkResponse createPaymentLink(Long bookingId) throws Exception {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt vé #" + bookingId));

        long orderCode = System.currentTimeMillis() % 1000000000L;
        int amount = booking.getTotalAmount().intValue();

        String description = booking.getBookingCode() != null ? booking.getBookingCode() : ("CNE" + bookingId);
        if (description.length() > 25) {
            description = description.substring(0, 25);
        }

        String rawData = "amount=" + amount +
                "&cancelUrl=" + cancelUrl +
                "&description=" + description +
                "&orderCode=" + orderCode +
                "&returnUrl=" + returnUrl;

        String signature = generateHmacSha256(rawData, checksumKey.trim());

        // 2. Chuẩn bị request body
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("orderCode", orderCode);
        requestBody.put("amount", amount);
        requestBody.put("description", description);
        requestBody.put("cancelUrl", cancelUrl);
        requestBody.put("returnUrl", returnUrl);
        requestBody.put("signature", signature);

        // 3. Chuẩn bị Header xác thực
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", clientId.trim());
        headers.set("x-api-key", apiKey.trim());

        var entity = new HttpEntity<>(requestBody, headers);

        // 4. Gọi trực tiếp API payOS bằng RestTemplate
        ResponseEntity<String> response = restTemplate.postForEntity(
                "https://api-merchant.payos.vn/v2/payment-requests",
                entity,
                String.class
        );

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseBody = objectMapper.readTree(response.getBody());

        if (responseBody == null || !"00".equals(responseBody.path("code").asText())) {
            String errorDesc = responseBody != null ? responseBody.path("desc").asText() : "Lỗi không xác định từ payOS";
            throw new RuntimeException("Lỗi payOS: " + errorDesc);
        }

        JsonNode data = responseBody.path("data");
        String checkoutUrl = data.path("checkoutUrl").asText();
        String qrCode = data.path("qrCode").asText();
        String status = data.path("status").asText();

        // 5. Lưu bản ghi Payment ở trạng thái PENDING
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setProvider("payOS");
        payment.setOrderCode(String.valueOf(orderCode));
        payment.setAmount(booking.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaymentUrl(checkoutUrl);
        paymentRepository.save(payment);

        return PaymentLinkResponse.builder()
                .orderCode(orderCode)
                .checkoutUrl(checkoutUrl)
                .qrCode(qrCode)
                .status(status)
                .build();
    }

    private String generateHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tạo chữ ký HMAC-SHA256: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void processWebhook(Webhook webhookBody) throws Exception {
        // SDK vẫn dùng rất tốt và an toàn cho việc xác thực webhook
        WebhookData webhookData = payOS.verifyPaymentWebhookData(webhookBody);
        if (webhookData == null) {
            throw new IllegalArgumentException("Dữ liệu webhook không hợp lệ");
        }

        String orderCode = String.valueOf(webhookData.getOrderCode());

        Payment payment = paymentRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch: " + orderCode));

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return;
        }

        if ("00".equals(webhookBody.getCode())) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);

            Booking booking = payment.getBooking();
            if (booking != null) {
                booking.setStatus(BookingStatus.CONFIRMED);
                bookingRepository.save(booking);
            }
        }
    }

        @Transactional
    public Map<String, Object> getPaymentStatusByBookingId(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt vé #" + bookingId));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            Payment payment = paymentRepository.findByBooking(booking).orElse(null);
            if (payment != null && payment.getOrderCode() != null) {
                try {
                    long orderCode = Long.parseLong(payment.getOrderCode());
                    vn.payos.type.PaymentLinkData paymentLinkData = payOS.getPaymentLinkInformation(orderCode);
                    // payOS trả về trạng thái "PAID", ta lưu vào DB của mình là SUCCESS
                    if (paymentLinkData != null && "PAID".equalsIgnoreCase(paymentLinkData.getStatus())) {
                        payment.setStatus(PaymentStatus.SUCCESS);
                        payment.setPaidAt(LocalDateTime.now());
                        paymentRepository.save(payment);

                        booking.setStatus(BookingStatus.CONFIRMED);
                        bookingRepository.save(booking);
                        log.info("Đã đồng bộ thanh toán thành công cho đơn đặt vé #{}", bookingId);
                    }
                } catch (Exception e) {
                    log.warn("Chưa ghi nhận thanh toán từ payOS cho order {}: {}", payment.getOrderCode(), e.getMessage());
                }
            }
        }

        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put("bookingId", booking.getBookingId());
        statusMap.put("bookingCode", booking.getBookingCode());
        statusMap.put("bookingStatus", booking.getStatus() != null ? booking.getStatus().name() : "HELD");
        
        boolean isConfirmed = booking.getStatus() == BookingStatus.CONFIRMED;
        statusMap.put("isPaid", isConfirmed);
        statusMap.put("status", isConfirmed ? "SUCCESS" : "PENDING");
        return statusMap;
    }
}