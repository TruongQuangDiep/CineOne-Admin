package com.example.cineone_admin.sync;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.cineone_admin.enums.ProviderEntityType.SHOWTIME;

class SyncLogServiceTests {
    @Test void largeUnicodeErrorListFitsMysqlTextAndRemainsValidJson() {
        var errors = new ArrayList<SyncRunResult.Error>();
        for (int i = 0; i < 3000; i++) errors.add(new SyncRunResult.Error(SHOWTIME, "s" + i, "s" + i,
                "Lỗi dữ liệu tiếng Việt \"xuống dòng\"\n".repeat(10)));
        var result = new SyncRunResult(1L, "CGV", "FAILED", LocalDate.of(2026, 10, 21), 3002, 2, 3000, 123, errors);
        String message = new SyncLogService(null).message(result);
        assertTrue(message.getBytes(StandardCharsets.UTF_8).length <= 65000);
        var json = JsonMapper.builder().build().readTree(message);
        assertEquals(3000, json.get("failed").asInt());
        assertTrue(json.get("errors").size() > 0);
        assertEquals(3000, json.get("errors").size() + json.get("errorsOmitted").asInt());
    }
}
