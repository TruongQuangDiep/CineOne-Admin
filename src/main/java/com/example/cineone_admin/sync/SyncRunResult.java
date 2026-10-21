package com.example.cineone_admin.sync;

import java.time.LocalDate;
import java.util.List;
import com.example.cineone_admin.enums.ProviderEntityType;

/** Counts committed catalog records (including unchanged records), not HTTP calls. */
public record SyncRunResult(Long syncLogId, String providerCode, String status, LocalDate referenceDate,
        int total, int success, int failed, long durationMs, List<Error> errors) {
    public SyncRunResult { errors = List.copyOf(errors); }
    public record Error(ProviderEntityType entityType, String externalId, String showtimeId, String reason) {}
}
