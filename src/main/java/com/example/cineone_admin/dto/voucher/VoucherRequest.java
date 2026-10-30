package com.example.cineone_admin.dto.voucher;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class VoucherRequest {
    @NotBlank(message = "Mã voucher không được để trống")
    private String code;

    private String description;
    private String imageUrl;

    @NotBlank(message = "Loại giảm giá không được để trống")
    private String discountType = "PERCENT"; // PERCENT hoặc FIXED

    @NotNull(message = "Giá trị giảm không được để trống")
    private BigDecimal discountValue;

    private BigDecimal minOrder = BigDecimal.ZERO;
    private BigDecimal maxDiscount;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startAt;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endAt;

    private Integer usageLimit;
    private String status = "ACTIVE";
}