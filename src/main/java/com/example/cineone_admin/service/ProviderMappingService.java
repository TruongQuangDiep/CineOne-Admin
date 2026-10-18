package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.sync.EntityCreationPair;
import com.example.cineone_admin.entity.ProviderMapping;
import com.example.cineone_admin.enums.*;
import java.util.*;
import java.util.function.Supplier;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Thử lại ngoài ranh giới giao dịch; không tiếp tục dùng giao dịch đã thất bại. */
@Service
public class ProviderMappingService {
    public static final int MAX_BATCH_SIZE = 500;
    private static final int MAX_ATTEMPTS = 3;
    private final ProviderMappingTransactionService transactions;

    public ProviderMappingService(ProviderMappingTransactionService transactions) {
        this.transactions = transactions;
    }

    public Optional<Long> findInternalId(Long providerId, ProviderEntityType type, String externalId) {
        return transactions.findInternalId(providerId, type, externalId);
    }

    public Optional<String> findExternalId(Long providerId, ProviderEntityType type, Long internalId) {
        return transactions.findExternalId(providerId, type, internalId);
    }

    public Map<String, Long> findInternalIds(Long providerId, ProviderEntityType type, Collection<String> ids) {
        return transactions.findInternalIds(providerId, type, ids);
    }

    public Map<Long, String> findExternalIds(Long providerId, ProviderEntityType type, Collection<Long> ids) {
        return transactions.findExternalIds(providerId, type, ids);
    }

    public Long resolveOrCreate(Long providerId, ProviderEntityType type, String externalId, Supplier<?> factory) {
        return write(() -> transactions.resolveOrCreate(providerId, type, externalId, factory),
                () -> transactions.findInternalId(providerId, type, externalId).isPresent());
    }

    public Map<String, Long> resolveOrCreateBatch(Long providerId, ProviderEntityType type,
            List<? extends EntityCreationPair<?>> items) {
        return write(() -> transactions.resolveOrCreateBatch(providerId, type, items),
                () -> transactions.findInternalIds(providerId, type,
                        items.stream().map(EntityCreationPair::externalId).toList()).size() == items.size());
    }

    public ProviderMapping bind(Long providerId, ProviderEntityType type, String externalId, Long internalId) {
        return write(() -> transactions.bind(providerId, type, externalId, internalId));
    }

    public Map<String, ProviderMapping> bindBatch(Long providerId, ProviderEntityType type, Map<String, Long> pairs) {
        return write(() -> transactions.bindBatch(providerId, type, pairs));
    }

    public void changeStatus(Long providerId, ProviderEntityType type, String externalId, ProviderMappingStatus status) {
        transactions.changeStatus(providerId, type, externalId, status);
    }

    public void changeStatusBatch(Long providerId, ProviderEntityType type, Collection<String> ids,
            ProviderMappingStatus status) {
        transactions.changeStatusBatch(providerId, type, ids, status);
    }

    private <T> T write(Supplier<T> operation) {
        return write(operation, () -> false);
    }

    private <T> T write(Supplier<T> operation, Supplier<Boolean> requestedMappingsNowExist) {
        // Nếu bên gọi đang có giao dịch, bên đó phải chịu trách nhiệm thử lại toàn giao dịch.
        if (TransactionSynchronizationManager.isActualTransactionActive()) return operation.get();
        for (int attempt = 1; ; attempt++) {
            try {
                return operation.get();
            } catch (RuntimeException failure) {
                if (attempt >= MAX_ATTEMPTS) throw failure;
                if (isMappingInsertConflict(failure)) continue;
                // Tên rạp/phòng có thể trùng trước khi INSERT mapping. Chỉ phục hồi khi
                // mapping đúng nguồn và ID đã được giao dịch khác xác nhận đầy đủ.
                if (!isUniqueViolation(failure) || !requestedMappingsNowExist.get()) throw failure;
            }
        }
    }

    private boolean isUniqueViolation(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                var sql = violation.getSQLException();
                return violation.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE
                        || "23505".equals(sql.getSQLState())
                        || (sql.getErrorCode() == 1062 && "23000".equals(sql.getSQLState()));
            }
        }
        return false;
    }

    private boolean isMappingInsertConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                if (name == null) return false;
                String normalized = name.toLowerCase(Locale.ROOT);
                // Nhận diện trực tiếp hai ràng buộc duy nhất đã được khai báo cho mapping.
                return normalized.contains("uq_provider_external") || normalized.contains("uq_provider_mapping");
            }
        }
        return false;
    }
}
