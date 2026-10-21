package com.example.cineone_admin.sync;

import com.example.cineone_admin.entity.*;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Service
public class SyncLogService {
    private final EntityManager em;
    private final JsonMapper json = JsonMapper.builder().build();
    public SyncLogService(EntityManager em) { this.em = em; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(Long providerId) {
        var log = new ProviderSyncLog();
        log.setProvider(em.getReference(DataProvider.class, providerId));
        log.setStatus("PROCESSING");
        log.setMessage("{\"total\":0,\"success\":0,\"failed\":0,\"durationMs\":0,\"errors\":[]}");
        em.persist(log);
        em.flush();
        return log.getSyncLogId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(SyncRunResult result) {
        var log = em.find(ProviderSyncLog.class, result.syncLogId());
        if (log == null) throw new IllegalStateException("Sync log no longer exists");
        log.setStatus(result.status());
        log.setMessage(message(result));
        em.flush();
    }

    // MySQL TEXT is limited to 65,535 bytes, not characters. Never cut a JSON string.
    String message(SyncRunResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", result.total()); body.put("success", result.success());
        body.put("failed", result.failed()); body.put("durationMs", result.durationMs());
        body.put("referenceDate", result.referenceDate().toString());
        int low = 0, high = result.errors().size();
        String encoded = "";
        while (low <= high) {
            int count = low + (high - low) / 2;
            body.put("errors", result.errors().subList(0, count));
            body.put("errorsOmitted", result.errors().size() - count);
            String candidate = json.writeValueAsString(body);
            if (candidate.getBytes(StandardCharsets.UTF_8).length <= 65000) {
                encoded = candidate; low = count + 1;
            } else high = count - 1;
        }
        return encoded;
    }
}
