package com.example.cineone_admin.controller;

import com.example.cineone_admin.entity.*;
import com.example.cineone_admin.repository.*;
import com.example.cineone_admin.service.SyncHistoryService;
import com.example.cineone_admin.sync.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sync15;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
@WithMockUser(roles = "ADMIN")
class SyncHistoryControllerIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired DataProviderRepository providers;
    @Autowired ProviderSyncLogRepository logs;
    @Autowired SyncLogService logService;
    @Autowired SyncHistoryService history;
    @Autowired ManualSyncService manual;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CinemaSyncService sync;
    @MockitoBean(name = "manualSyncExecutor") ThreadPoolTaskExecutor executor;
    MockMvc mvc;
    DataProvider active, stopped, unsupported;
    List<Runnable> tasks;

    @BeforeEach void setup() {
        logs.deleteAll(); providers.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        active = provider("CGV", "CGV", "ACTIVE");
        stopped = provider("LOTTE", "Lotte", "INACTIVE");
        unsupported = provider("NEW", "Nguồn chưa hỗ trợ", "ACTIVE");
        tasks = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> { tasks.add(invocation.getArgument(0)); return null; })
                .when(executor).execute(any(Runnable.class));
    }

    @Test void rendersDesignFiltersAndEscapesLegacyMessages() throws Exception {
        var log = log(active, "FAILED", "<script>alert('x')</script>", "2026-10-25 23:59:59");
        mvc.perform(get("/sync-logs")).andExpect(status().isOk())
                .andExpect(content().string(containsString("LỊCH SỬ ĐỒNG BỘ")))
                .andExpect(content().string(containsString("Đồng bộ ngay")))
                .andExpect(content().string(not(containsString("Không có nhật ký"))));
        mvc.perform(get("/sync-logs/{id}", log.getSyncLogId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.detail").value("<script>alert('x')</script>"))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertNull(history.get(log.getSyncLogId()).success());
        String preview = System.getProperty("sync.preview.dir");
        if (preview != null) {
            var directory = java.nio.file.Path.of(preview);
            java.nio.file.Files.createDirectories(directory);
            java.nio.file.Files.writeString(directory.resolve("sync.html"), mvc.perform(get("/sync-logs"))
                    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test void combinesFiltersIncludesEndOfDayAndKeepsStoppedProviderHistory() throws Exception {
        log(active, "SUCCESS", "{}", "2026-10-25 00:00:00");
        log(active, "FAILED", "{}", "2026-10-25 23:59:59");
        log(active, "FAILED", "{}", "2026-10-26 00:00:00");
        log(stopped, "FAILED", "{}", "2026-10-25 12:00:00");
        var page = history.search(active.getProviderId(), "FAILED", LocalDate.of(2026,10,25), LocalDate.of(2026,10,25), 0);
        assertEquals(1, page.getTotalElements());
        assertEquals(1, history.search(stopped.getProviderId(), "", null, null, 0).getTotalElements());
        mvc.perform(get("/sync-logs").param("fromDate", "2026-10-26").param("toDate", "2026-10-25"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/sync-logs").param("status", "UNKNOWN")).andExpect(status().isBadRequest());
        mvc.perform(get("/sync-logs").param("fromDate", "bad")).andExpect(status().isBadRequest());
        mvc.perform(get("/sync-logs/999999")).andExpect(status().isNotFound());
    }

    @Test void paginationRetainsEveryFilterAndEmptyStateRenders() throws Exception {
        for (int i = 0; i < 12; i++) log(active, "SUCCESS", "{}", "2026-10-25 12:00:00");
        mvc.perform(get("/sync-logs").param("providerId", active.getProviderId().toString())
                        .param("status", "SUCCESS").param("fromDate", "2026-10-25").param("toDate", "2026-10-25"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("page=1")))
                .andExpect(content().string(containsString("status=SUCCESS")))
                .andExpect(content().string(containsString("fromDate=2026-10-25")))
                .andExpect(content().string(containsString("toDate=2026-10-25")));
        mvc.perform(get("/sync-logs").param("status", "FAILED")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Không có nhật ký đồng bộ phù hợp.")));
    }

    @Test void acceptsInBackgroundRejectsDuplicatesAndExposesPartialFailure() throws Exception {
        mvc.perform(post("/sync-logs/start").with(csrf()).param("providerId", active.getProviderId().toString()))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.id").isNumber());
        assertEquals(1, tasks.size());
        verifyNoInteractions(sync);
        var log = logs.findAll().getFirst();
        mvc.perform(get("/sync-logs/{id}", log.getSyncLogId())).andExpect(jsonPath("$.status").value("PROCESSING"));
        mvc.perform(post("/sync-logs/start").with(csrf()).param("providerId", active.getProviderId().toString()))
                .andExpect(status().isConflict());
        when(sync.syncStarted(eq("CGV"), any(), eq(log.getSyncLogId()))).thenAnswer(invocation -> {
            var request = (com.example.cineone_admin.dto.sync.SyncRequestDTO) invocation.getArgument(1);
            assertEquals(6, request.entityTypes().size());
            assertEquals(com.example.cineone_admin.enums.SyncMode.FULL, request.mode());
            var result = new SyncRunResult(log.getSyncLogId(), "CGV", "FAILED", LocalDate.now(),
                    3, 2, 1, 100, List.of(new SyncRunResult.Error(null, "bad", null, "Dữ liệu lỗi")));
            logService.finish(result); return result;
        });
        tasks.getFirst().run();
        mvc.perform(get("/sync-logs/{id}", log.getSyncLogId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.success").value(2))
                .andExpect(jsonPath("$.failed").value(1)).andExpect(jsonPath("$.detail", containsString("Dữ liệu lỗi")));
        assertNotNull(manual.start(active.getProviderId()));
    }

    @Test void concurrentClaimsProduceExactlyOneBackgroundTask() throws Exception {
        var barrier = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> call = () -> { barrier.await(); try { manual.start(active.getProviderId()); return true; }
                catch (org.springframework.web.server.ResponseStatusException ex) {
                    assertEquals(409, ex.getStatusCode().value()); return false;
                } };
            var a = pool.submit(call); var b = pool.submit(call); barrier.countDown();
            assertNotEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, tasks.size()); assertEquals(1, logs.count());
    }

    @Test void enforcesAdminCsrfAndProviderEligibility() throws Exception {
        mvc.perform(get("/sync-logs").with(anonymous())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/sync-logs/1").with(user("member").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/sync-logs/start").param("providerId", active.getProviderId().toString()))
                .andExpect(status().isForbidden());
        for (var p : List.of(stopped, unsupported))
            mvc.perform(post("/sync-logs/start").with(csrf()).param("providerId", p.getProviderId().toString()))
                    .andExpect(status().isBadRequest());
        mvc.perform(post("/sync-logs/start").with(csrf()).param("providerId", "999999"))
                .andExpect(status().isNotFound());
        assertEquals(0, logs.count());
    }

    @Test void workerAndQueueFailuresNeverClaimSuccess() {
        Long id = manual.start(active.getProviderId());
        when(sync.syncStarted(anyString(), any(), anyLong())).thenThrow(new IllegalStateException("secret-path"));
        tasks.getFirst().run();
        assertEquals("FAILED", history.get(id).status());
        assertNull(history.get(id).success());
        assertFalse(history.get(id).detail().contains("secret-path"));
        doThrow(new org.springframework.core.task.TaskRejectedException("full")).when(executor).execute(any(Runnable.class));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> manual.start(active.getProviderId()));
        assertTrue(logs.findAll().stream().allMatch(l -> l.getStatus().equals("FAILED")));
    }

    private DataProvider provider(String code, String name, String status) {
        var p = new DataProvider(); p.setCode(code); p.setName(name); p.setStatus(status);
        p.setProviderType("CINEMA"); p.setMock(true); return providers.saveAndFlush(p);
    }
    private ProviderSyncLog log(DataProvider provider, String status, String message, String date) {
        var l = new ProviderSyncLog(); l.setProvider(provider); l.setStatus(status); l.setMessage(message);
        l = logs.saveAndFlush(l);
        jdbc.update("update providersynclogs set created_at = ? where sync_log_id = ?", date, l.getSyncLogId());
        return l;
    }
}
