package com.example.cineone_admin.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLinkResponse {
    private Long orderCode;
    private String checkoutUrl;
    private String qrCode;
    private String status;
}