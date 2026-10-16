package com.example.cineone_admin.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "cinemas")
@Getter
@Setter
public class Cinema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cinema_id")
    private Long cinemaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private CinemaBrand brand;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 10)
    private String status = "ACTIVE";
}