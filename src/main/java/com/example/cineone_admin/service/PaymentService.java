package com.example.cineone_admin.service;

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

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPayments(String keyword, PaymentStatus status, Pageable pageable) {
        return paymentRepository.searchPayments(keyword, status, pageable).map(this::mapToResponse);
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
            payment.setStatus(status.name());
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
            try {
                res.setStatus(PaymentStatus.valueOf(payment.getStatus().toUpperCase()));
            } catch (IllegalArgumentException e) {
                res.setStatus(PaymentStatus.PENDING);
            }
        }

        res.setPaymentUrl(payment.getPaymentUrl());
        res.setPaidAt(payment.getPaidAt());
        res.setCreatedAt(payment.getCreatedAt());
        return res;
    }
}