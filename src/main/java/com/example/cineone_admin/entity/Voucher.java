package com.example.cineone_admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vouchers")
@Getter
@Setter
public class Voucher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voucher_id")
    private Long voucherId;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "discount_type", nullable = false, length = 10)
    private String discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order", nullable = false, precision = 12, scale = 2)
    private BigDecimal minOrder = BigDecimal.ZERO;

    @Column(name = "max_discount", precision = 12, scale = 2)
    private BigDecimal maxDiscount;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(nullable = false, length = 10)
    private String status = "ACTIVE";
}