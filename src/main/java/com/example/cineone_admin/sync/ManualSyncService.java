package com.example.cineone_admin.sync;

import com.example.cineone_admin.dto.sync.SyncRequestDTO;
import com.example.cineone_admin.enums.ProviderEntityType;
import com.example.cineone_admin.enums.SyncMode;
import com.example.cineone_admin.service.ProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.Set;

@Service
public class ManualSyncService {
    private static final Logger LOG = LoggerFactory.getLogger(ManualSyncService.class);
    private final ProviderService providers;
    private final CinemaSyncAdapterFactory adapters;
    private final SyncLogService logs;
    private final CinemaSyncService sync;
    private final TaskExecutor executor;

    public ManualSyncService(ProviderService providers, CinemaSyncAdapterFactory adapters,
            SyncLogService logs, CinemaSyncService sync,
            @Qualifier("manualSyncExecutor") TaskExecutor executor) {
        this.providers = providers; this.adapters = adapters; this.logs = logs;
        this.sync = sync; this.executor = executor;
    }

    public Long start(Long providerId) {
        var provider = providers.get(providerId);
        if (!"ACTIVE".equals(provider.getStatus()) || !adapters.supports(provider.getCode()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nhà cung cấp phải đang hoạt động và đã có adapter đồng bộ.");
        Long logId;
        try { logId = logs.start(providerId); }
        catch (SyncAlreadyRunningException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
        var request = new SyncRequestDTO(Set.of(ProviderEntityType.CINEMA_BRAND, ProviderEntityType.CINEMA,
                ProviderEntityType.ROOM, ProviderEntityType.SEAT, ProviderEntityType.FNB_ITEM,
                ProviderEntityType.SHOWTIME), SyncMode.FULL, null, null, null);
        try {
            executor.execute(() -> {
                try { sync.syncStarted(provider.getCode(), request, logId); }
                catch (RuntimeException ex) {
                    LOG.error("Manual sync {} interrupted", logId, ex);
                    logs.failUnfinished(logId);
                }
            });
        } catch (org.springframework.core.task.TaskRejectedException ex) {
            logs.failUnfinished(logId);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Hệ thống đang bận. Vui lòng thử lại sau.");
        }
        return logId;
    }
}
