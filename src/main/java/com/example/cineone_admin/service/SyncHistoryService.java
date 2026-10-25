package com.example.cineone_admin.service;

import com.example.cineone_admin.entity.ProviderSyncLog;
import com.example.cineone_admin.repository.ProviderSyncLogRepository;
import com.example.cineone_admin.sync.CinemaSyncAdapterFactory;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SyncHistoryService {
    private final ProviderSyncLogRepository logs;
    private final ProviderService providers;
    private final CinemaSyncAdapterFactory adapters;
    private final JsonMapper json = JsonMapper.builder().build();

    public List<ProviderOption> options() {
        return providers.all().stream().map(p -> new ProviderOption(p.getProviderId(), p.getName(),
                "ACTIVE".equals(p.getStatus()) && adapters.supports(p.getCode()),
                logs.existsByProviderProviderIdAndStatus(p.getProviderId(), "PROCESSING"))).toList();
    }

    public Page<LogRow> search(Long providerId, String status, LocalDate from, LocalDate to, int page) {
        if (status != null && !status.isBlank() && !Set.of("SUCCESS", "FAILED", "PROCESSING").contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ.");
        if (from != null && to != null && from.isAfter(to) || to != null && to.getYear() >= 9999)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng ngày không hợp lệ.");
        if (providerId != null) providers.get(providerId);
        return logs.findAll((root, query, cb) -> {
            if (query.getResultType() != Long.class) root.fetch("provider");
            var filters = new ArrayList<Predicate>();
            if (providerId != null) filters.add(cb.equal(root.get("provider").get("providerId"), providerId));
            if (status != null && !status.isBlank()) filters.add(cb.equal(root.get("status"), status));
            if (from != null) filters.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            if (to != null) filters.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            return cb.and(filters.toArray(Predicate[]::new));
        }, PageRequest.of(Math.max(0, page), 10, Sort.by(Sort.Direction.DESC, "syncLogId"))).map(this::row);
    }

    public LogRow get(Long id) {
        return row(logs.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhật ký đồng bộ.")));
    }

    private LogRow row(ProviderSyncLog log) {
        String message = Objects.requireNonNullElse(log.getMessage(), "");
        Long total = null, success = null, failed = null, duration = null;
        String detail = message;
        try {
            var node = json.readTree(message);
            if (node != null && node.isObject()) {
                total = number(node, "total"); success = number(node, "success");
                failed = number(node, "failed"); duration = number(node, "durationMs");
                detail = json.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            }
        } catch (RuntimeException ignored) { /* Historical free-text logs remain readable. */ }
        String summary = "PROCESSING".equals(log.getStatus()) ? "Đang đồng bộ…"
                : success != null && failed != null ? "Thành công: " + success + " · Thất bại: " + failed
                : "Xem chi tiết nhật ký";
        return new LogRow(log.getSyncLogId(), log.getProvider().getName(), log.getStatus(),
                log.getCreatedAt(), total, success, failed, duration, summary, detail);
    }

    private static Long number(tools.jackson.databind.JsonNode node, String key) {
        var value = node.path(key);
        return value.isIntegralNumber() && value.asLong() >= 0 ? value.asLong() : null;
    }

    public record ProviderOption(Long id, String name, boolean eligible, boolean processing) {}
    public record LogRow(Long id, String providerName, String status, LocalDateTime createdAt,
            Long total, Long success, Long failed, Long durationMs, String summary, String detail) {}
}
