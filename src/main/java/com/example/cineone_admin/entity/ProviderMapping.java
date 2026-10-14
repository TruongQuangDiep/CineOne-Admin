package com.example.cineone_admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "ProviderMappings",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_provider_mapping",
            columnNames = {"provider_id", "entity_type", "internal_id"}
        ),
        @UniqueConstraint(
            name = "uq_provider_external",
            columnNames = {"provider_id", "entity_type", "external_id"}
        )
    }
)
@Getter
@Setter
public class ProviderMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mapping_id")
    private Long mappingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private DataProvider provider;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "internal_id", nullable = false)
    private Long internalId;

    @Column(name = "external_id", nullable = false, length = 255)
    private String externalId;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(nullable = false, length = 10)
    private String status = "ACTIVE";
}