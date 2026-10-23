package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.provider.ProviderRequest;
import com.example.cineone_admin.entity.*;
import com.example.cineone_admin.enums.ProviderEntityType;
import com.example.cineone_admin.repository.*;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProviderService {
    private final DataProviderRepository providers;
    private final ProviderMappingRepository mappings;
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE", "ERROR");

    public Page<DataProvider> search(String q, int page) {
        String term = like(q);
        return providers.findAll((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), term, '\\'),
                cb.like(cb.lower(root.get("code")), term, '\\')),
                PageRequest.of(Math.max(0, page), 10, Sort.by("providerId")));
    }

    public List<DataProvider> all() { return providers.findAll(Sort.by("name")); }

    public DataProvider get(Long id) {
        return providers.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhà cung cấp"));
    }

    @Transactional
    public DataProvider save(Long id, @Valid ProviderRequest request) {
        DataProvider provider = id == null ? new DataProvider() : get(id);
        // Adapter registration and existing references depend on this code.
        if (id != null && !provider.getCode().equals(request.getCode()))
            throw new IllegalArgumentException("Không thể đổi mã nhà cung cấp đã tạo");
        providers.findByCode(request.getCode()).ifPresent(existing -> {
            if (!Objects.equals(existing.getProviderId(), id))
                throw new IllegalArgumentException("Mã nhà cung cấp đã tồn tại");
        });
        provider.setCode(request.getCode());
        provider.setName(request.getName());
        provider.setProviderType(request.getProviderType());
        provider.setMock(request.isMock());
        return providers.saveAndFlush(provider);
    }

    @Transactional
    public void setEnabled(Long id, boolean enabled) {
        // Preserve source data, mapping states and IDs when cooperation stops.
        get(id).setStatus(enabled ? "ACTIVE" : "INACTIVE");
    }

    public Page<MappingRow> searchMappings(Long providerId, ProviderEntityType entity, String status,
                                           String q, int page, boolean includeStopped) {
        if (status != null && !status.isBlank() && !STATUSES.contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ");
        if (providerId != null) get(providerId);
        String search = q == null ? "" : q.strip();
        return mappings.findAll((root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            if (providerId != null) filters.add(cb.equal(root.get("provider").get("providerId"), providerId));
            if (!includeStopped) filters.add(cb.notEqual(root.get("provider").get("status"), "INACTIVE"));
            if (entity != null) filters.add(cb.equal(root.get("entityType"), entity.name()));
            if (status != null && !status.isBlank()) filters.add(cb.equal(root.get("status"), status));
            if (!search.isEmpty()) {
                Predicate external = cb.like(cb.lower(root.get("externalId")), like(search), '\\');
                try { filters.add(cb.or(external, cb.equal(root.get("internalId"), Long.valueOf(search)))); }
                catch (NumberFormatException ex) { filters.add(external); }
            }
            return cb.and(filters.toArray(Predicate[]::new));
        }, PageRequest.of(Math.max(0, page), 10, Sort.by(Sort.Direction.DESC, "mappingId")))
                .map(ProviderService::row);
    }

    public MappingRow mapping(Long id) {
        return row(mappings.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mapping")));
    }

    private static MappingRow row(ProviderMapping m) {
        var p = m.getProvider();
        return new MappingRow(m.getMappingId(), p.getProviderId(), p.getName(), p.getStatus(),
                m.getEntityType(), m.getInternalId(), m.getExternalId(), m.getLastSyncedAt(), m.getStatus());
    }

    private static String like(String value) {
        String text = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    public record MappingRow(Long mappingId, Long providerId, String providerName, String providerStatus,
            String entityType, Long internalId, String externalId, LocalDateTime lastSyncedAt, String status) {}
}
