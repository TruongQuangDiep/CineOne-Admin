package com.example.cineone_admin.dto.payment;

import com.example.cineone_admin.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentResponse {
    private Long paymentId;
    private Long bookingId;
    private String provider;
    private String orderCode;   
    private BigDecimal amount;
    private PaymentStatus status;
    private String paymentUrl;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime paidAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}