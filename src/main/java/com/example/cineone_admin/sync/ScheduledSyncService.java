package com.example.cineone_admin.sync;

import com.example.cineone_admin.dto.sync.SyncRequestDTO;
import com.example.cineone_admin.entity.DataProvider;
import com.example.cineone_admin.enums.ProviderEntityType;
import com.example.cineone_admin.enums.SyncMode;
import com.example.cineone_admin.repository.DataProviderRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ScheduledSyncService {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduledSyncService.class);
    static final Set<ProviderEntityType> FULL_ENTITIES = Set.of(
            ProviderEntityType.CINEMA_BRAND, ProviderEntityType.CINEMA,
            ProviderEntityType.ROOM, ProviderEntityType.SEAT,
            ProviderEntityType.FNB_ITEM, ProviderEntityType.SHOWTIME);

    private final DataProviderRepository providers;
    private final CinemaSyncAdapterFactory adapters;
    private final SyncLogService logs;
    private final CinemaSyncService sync;

    public ScheduledSyncService(DataProviderRepository providers, CinemaSyncAdapterFactory adapters,
            SyncLogService logs, CinemaSyncService sync) {
        this.providers = providers;
        this.adapters = adapters;
        this.logs = logs;
        this.sync = sync;
    }

    @Scheduled(cron = "${cineone.sync.cron:0 0 2 * * *}", zone = "${cineone.sync.zone:Asia/Ho_Chi_Minh}")
    public List<SyncRunResult> syncAllActive() {
        LOG.info("Starting scheduled cinema sync for all active providers");
        var request = new SyncRequestDTO(FULL_ENTITIES, SyncMode.FULL, null, null, null);
        List<SyncRunResult> results = new ArrayList<>();
        for (DataProvider provider : providers.findAll(Sort.by("providerId"))) {
            if (!"ACTIVE".equals(provider.getStatus()) || !adapters.supports(provider.getCode())) continue;
            Long logId;
            try {
                logId = logs.start(provider.getProviderId());
            } catch (RuntimeException ex) {
                LOG.warn("Skipping scheduled sync for provider {}: {}", provider.getCode(), ex.getMessage());
                continue;
            }
            try {
                var result = sync.syncStarted(provider.getCode(), request, logId);
                results.add(result);
                LOG.info("Finished scheduled sync {} for provider {}: status={}, success={}, failed={}",
                        logId, provider.getCode(), result.status(), result.success(), result.failed());
            } catch (RuntimeException ex) {
                LOG.error("Scheduled sync {} for provider {} interrupted", logId, provider.getCode(), ex);
                logs.failUnfinished(logId);
            }
        }
        LOG.info("Completed scheduled cinema sync run ({} provider(s) processed)", results.size());
        return List.copyOf(results);
    }
}
