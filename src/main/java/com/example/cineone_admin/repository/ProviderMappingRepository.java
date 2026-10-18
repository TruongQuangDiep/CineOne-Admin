package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.ProviderMapping;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderMappingRepository extends JpaRepository<ProviderMapping, Long> {
    Optional<ProviderMapping> findByProviderProviderIdAndEntityTypeAndExternalId(
            Long providerId, String entityType, String externalId);

    Optional<ProviderMapping> findByProviderProviderIdAndEntityTypeAndInternalId(
            Long providerId, String entityType, Long internalId);
    List<ProviderMapping> findByProviderProviderIdAndEntityTypeAndExternalIdIn(
            Long providerId, String type, Collection<String> externalIds);

    List<ProviderMapping> findByProviderProviderIdAndEntityTypeAndInternalIdIn(
            Long providerId, String type, Collection<Long> internalIds);

    // Đọc có khóa lấy dữ liệu hiện tại kể cả khi giao dịch MySQL đã có ảnh chụp cũ.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ProviderMapping> findForUpdateByProviderProviderIdAndEntityTypeAndExternalIdIn(
            Long providerId, String type, Collection<String> externalIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ProviderMapping> findForUpdateByProviderProviderIdAndEntityTypeAndInternalIdIn(
            Long providerId, String type, Collection<Long> internalIds);
}