package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.ProviderSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProviderSyncLogRepository extends JpaRepository<ProviderSyncLog, Long>,
        JpaSpecificationExecutor<ProviderSyncLog> {
    boolean existsByProviderProviderIdAndStatus(Long providerId, String status);
}
