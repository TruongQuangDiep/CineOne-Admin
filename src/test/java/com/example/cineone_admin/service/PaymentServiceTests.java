package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.payment.PaymentResponse;
import com.example.cineone_admin.entity.Booking;
import com.example.cineone_admin.entity.Payment;
import com.example.cineone_admin.enums.PaymentStatus;
import com.example.cineone_admin.exception.ResourceNotFoundException;
import com.example.cineone_admin.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTests {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    private Payment mockPayment;

    @BeforeEach
    void setUp() {
        mockPayment = new Payment();
        mockPayment.setPaymentId(100L);
        mockPayment.setProvider("payOS");
        mockPayment.setOrderCode("PAYOS-998877");
        mockPayment.setAmount(BigDecimal.valueOf(150000));
        mockPayment.setStatus(PaymentStatus.PAID);
        mockPayment.setPaymentUrl("https://pay.payos.vn/web/test");
        mockPayment.setCreatedAt(LocalDateTime.now());
        mockPayment.setPaidAt(LocalDateTime.now());

        Booking booking = new Booking();
        booking.setBookingId(55L);
        mockPayment.setBooking(booking);
    }

    @Test
    void getPayments_ReturnsCorrectPage() {
        var pageable = PageRequest.of(0, 10);
        when(paymentRepository.searchPayments(eq("PAYOS"), eq(PaymentStatus.PAID), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(mockPayment)));

                Page<PaymentResponse> result = paymentService.getPayments("PAYOS", PaymentStatus.PAID.name(), pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        PaymentResponse res = result.getContent().get(0);
        assertEquals("PAYOS-998877", res.getOrderCode());
        assertEquals(55L, res.getBookingId());
        assertEquals(PaymentStatus.PAID, res.getStatus());
    }

    @Test
    void getPaymentById_ThrowsExceptionWhenNotFound() {
        when(paymentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> paymentService.getPaymentById(404L));
    }

    @Test
    void updatePaymentStatus_UpdatesStatusCorrectly() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(mockPayment));

        paymentService.updatePaymentStatus(100L, PaymentStatus.FAILED);

        assertEquals(PaymentStatus.FAILED, mockPayment.getStatus());
        verify(paymentRepository, times(1)).save(mockPayment);
    }
}