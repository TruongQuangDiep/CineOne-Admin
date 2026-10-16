package com.example.cineone_admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "dataproviders")
@Getter
@Setter
public class DataProvider {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "provider_id")
    private Long providerId;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "provider_type", nullable = false, length = 50)
    private String providerType;

    @Column(name = "is_mock", nullable = false)
    private Boolean mock = false;

    @Column(nullable = false, length = 10)
    private String status = "ACTIVE";
}