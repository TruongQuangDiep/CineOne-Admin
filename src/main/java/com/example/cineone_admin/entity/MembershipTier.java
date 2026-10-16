package com.example.cineone_admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "membershiptiers")
@Getter
@Setter
public class MembershipTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "membership_tier_id")
    private Long membershipTierId;

    @Column(nullable = false, unique = true, length = 30)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal minSpending = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 10)
    private String status = "ACTIVE";
}