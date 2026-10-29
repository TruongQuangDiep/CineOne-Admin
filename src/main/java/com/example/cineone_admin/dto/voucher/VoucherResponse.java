package com.example.cineone_admin.dto.voucher;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VoucherResponse {
    private Long voucherId;
    private String code;
    private String description;
    private String imageUrl;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrder;
    private BigDecimal maxDiscount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;

    private Integer usageLimit;
    private String status;
}