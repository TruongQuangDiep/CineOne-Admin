package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.payment.PaymentLinkResponse;
import com.example.cineone_admin.entity.Booking;
import com.example.cineone_admin.enums.BookingStatus;
import com.example.cineone_admin.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Value;
import vn.payos.PayOS;
import vn.payos.type.CheckoutResponseData;
import vn.payos.type.ItemData;
import vn.payos.type.PaymentData;
import vn.payos.type.Webhook;
import vn.payos.type.WebhookData;

import java.time.LocalDateTime;
import java.util.List;
import com.example.cineone_admin.dto.payment.PaymentResponse;
import com.example.cineone_admin.entity.Payment;
import com.example.cineone_admin.enums.PaymentStatus;
import com.example.cineone_admin.exception.ResourceNotFoundException;
import com.example.cineone_admin.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final PayOS payOS;

    @Value("${payos.return-url}")
    private String returnUrl;

    @Value("${payos.cancel-url}")
    private String cancelUrl;

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

        // payOS yêu cầu orderCode là số nguyên dương duy nhất
        long orderCode = System.currentTimeMillis();
        int amount = booking.getTotalAmount().intValue();

        String description = "CineOne " + bookingId;
        if (description.length() > 25) {
            description = description.substring(0, 25);
        }

        List<ItemData> items = List.of(
                ItemData.builder()
                        .name("Ve xem phim CineOne")
                        .quantity(1)
                        .price(amount)
                        .build()
        );

        PaymentData paymentData = PaymentData.builder()
                .orderCode(orderCode)
                .amount(amount)
                .description(description)
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .items(items)
                .build();

        CheckoutResponseData payosResponse = payOS.createPaymentLink(paymentData);

        // Lưu bản ghi Payment ở trạng thái PENDING
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setProvider("payOS");
        payment.setOrderCode(String.valueOf(orderCode));
        payment.setAmount(booking.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaymentUrl(payosResponse.getCheckoutUrl());
        paymentRepository.save(payment);

        return PaymentLinkResponse.builder()
                .orderCode(orderCode)
                .checkoutUrl(payosResponse.getCheckoutUrl())
                .qrCode(payosResponse.getQrCode())
                .status(payosResponse.getStatus())
                .build();
    }

    @Transactional
    public void processWebhook(Webhook webhookBody) throws Exception {
        // 1. Xác thực chữ ký số bằng SDK payOS để chống giả mạo request
        WebhookData webhookData = payOS.verifyPaymentWebhookData(webhookBody);
        if (webhookData == null) {
            throw new IllegalArgumentException("Dữ liệu webhook không hợp lệ");
        }

        String orderCode = String.valueOf(webhookData.getOrderCode());

        // 2. Tìm bản ghi giao dịch trong database
        Payment payment = paymentRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch: " + orderCode));

        // 3. XỬ LÝ IDEMPOTENCY: Nếu đơn đã ghi nhận thanh toán rồi thì dừng, không cập nhật trùng lặp
        if (payment.getStatus() == PaymentStatus.PAID) {
            return;
        }

        // 4. Mã code "00" biểu thị chuyển khoản thành công
        if ("00".equals(webhookBody.getCode())) {
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);

            // Cập nhật đơn đặt vé sang trạng thái CONFIRMED
            Booking booking = payment.getBooking();
            if (booking != null) {
                booking.setStatus(BookingStatus.CONFIRMED);
                bookingRepository.save(booking);
            }
        }
    }
}