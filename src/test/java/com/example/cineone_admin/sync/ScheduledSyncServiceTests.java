package com.example.cineone_admin.sync;

import com.example.cineone_admin.dto.sync.SyncRequestDTO;
import com.example.cineone_admin.entity.DataProvider;
import com.example.cineone_admin.enums.ProviderEntityType;
import com.example.cineone_admin.enums.SyncMode;
import com.example.cineone_admin.repository.DataProviderRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScheduledSyncServiceTests {
    private static DataProvider provider(Long id, String code, String status) {
        var p = new DataProvider();
        p.setProviderId(id);
        p.setCode(code);
        p.setName(code);
        p.setProviderType("CINEMA");
        p.setStatus(status);
        return p;
    }

    @Test
    void syncsOnlyActiveSupportedProvidersInOrder() {
        var providers = mock(DataProviderRepository.class);
        var adapters = mock(CinemaSyncAdapterFactory.class);
        var logs = mock(SyncLogService.class);
        var sync = mock(CinemaSyncService.class);
        var cgv = provider(1L, "CGV", "ACTIVE");
        var tmdb = provider(2L, "TMDB", "ACTIVE");
        var lotte = provider(3L, "LOTTE", "INACTIVE");
        var beta = provider(4L, "BETA", "ACTIVE");

        when(providers.findAll(Sort.by("providerId"))).thenReturn(List.of(cgv, tmdb, lotte, beta));
        when(adapters.supports("CGV")).thenReturn(true);
        when(adapters.supports("TMDB")).thenReturn(false);
        when(adapters.supports("LOTTE")).thenReturn(true);
        when(adapters.supports("BETA")).thenReturn(true);
        when(logs.start(1L)).thenReturn(101L);
        when(logs.start(4L)).thenReturn(104L);

        var expectedRequest = new SyncRequestDTO(EnumSet.of(
                ProviderEntityType.CINEMA_BRAND, ProviderEntityType.CINEMA,
                ProviderEntityType.ROOM, ProviderEntityType.SEAT,
                ProviderEntityType.FNB_ITEM, ProviderEntityType.SHOWTIME),
                SyncMode.FULL, null, null, null);
        var cgvResult = new SyncRunResult(101L, "CGV", "SUCCESS", LocalDate.of(2026, 11, 14), 10, 10, 0, 50, List.of());
        var betaResult = new SyncRunResult(104L, "BETA", "SUCCESS", LocalDate.of(2026, 11, 14), 8, 8, 0, 40, List.of());
        when(sync.syncStarted("CGV", expectedRequest, 101L)).thenReturn(cgvResult);
        when(sync.syncStarted("BETA", expectedRequest, 104L)).thenReturn(betaResult);

        var scheduler = new ScheduledSyncService(providers, adapters, logs, sync);
        var results = scheduler.syncAllActive();

        assertEquals(List.of(cgvResult, betaResult), results);
        InOrder order = inOrder(logs, sync);
        order.verify(logs).start(1L);
        order.verify(sync).syncStarted("CGV", expectedRequest, 101L);
        order.verify(logs).start(4L);
        order.verify(sync).syncStarted("BETA", expectedRequest, 104L);
        verify(logs, never()).start(2L);
        verify(logs, never()).start(3L);
    }

    @Test
    void continuesWhenOneProviderIsAlreadyRunningOrInterrupted() {
        var providers = mock(DataProviderRepository.class);
        var adapters = mock(CinemaSyncAdapterFactory.class);
        var logs = mock(SyncLogService.class);
        var sync = mock(CinemaSyncService.class);
        var cgv = provider(1L, "CGV", "ACTIVE");
        var beta = provider(2L, "BETA", "ACTIVE");
        var megags = provider(3L, "MEGAGS", "ACTIVE");

        when(providers.findAll(Sort.by("providerId"))).thenReturn(List.of(cgv, beta, megags));
        when(adapters.supports(anyString())).thenReturn(true);
        when(logs.start(1L)).thenThrow(new SyncAlreadyRunningException());
        when(logs.start(2L)).thenReturn(202L);
        when(logs.start(3L)).thenReturn(203L);
        when(sync.syncStarted(eq("BETA"), any(), eq(202L))).thenThrow(new IllegalStateException("interrupted"));
        var megagsResult = new SyncRunResult(203L, "MEGAGS", "SUCCESS", LocalDate.of(2026, 11, 14), 5, 5, 0, 30, List.of());
        when(sync.syncStarted(eq("MEGAGS"), any(), eq(203L))).thenReturn(megagsResult);

        var scheduler = new ScheduledSyncService(providers, adapters, logs, sync);
        var results = scheduler.syncAllActive();

        assertEquals(List.of(megagsResult), results);
        verify(logs).failUnfinished(202L);
        verify(logs, never()).failUnfinished(203L);
    }

    @Test
    void schedulesDailyAtTwoAmVietnamTime() throws NoSuchMethodException {
        var method = ScheduledSyncService.class.getDeclaredMethod("syncAllActive");
        var scheduled = method.getAnnotation(Scheduled.class);
        assertNotNull(scheduled);
        assertEquals("${cineone.sync.cron:0 0 2 * * *}", scheduled.cron());
        assertEquals("${cineone.sync.zone:Asia/Ho_Chi_Minh}", scheduled.zone());

        var cron = CronExpression.parse("0 0 2 * * *");
        var zone = ZoneId.of("Asia/Ho_Chi_Minh");
        var beforeTwoAm = ZonedDateTime.of(2026, 11, 14, 1, 59, 59, 0, zone);
        var atTwoAm = cron.next(beforeTwoAm);
        assertEquals(ZonedDateTime.of(2026, 11, 14, 2, 0, 0, 0, zone), atTwoAm);
        assertEquals(ZonedDateTime.of(2026, 11, 15, 2, 0, 0, 0, zone), cron.next(atTwoAm));
    }
}
