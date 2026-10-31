package com.example.cineone_admin.service;

import com.example.cineone_admin.config.GeminiProperties;
import com.example.cineone_admin.dto.ai.SearchCriteria;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named = "CINEONE_GEMINI_LIVE_TEST", matches = "true")
class GeminiLiveSmokeTest {
    @Test void extractsSyntheticSearchUsingRealGemini() {
        var properties = new GeminiProperties();
        properties.setApiKey(System.getenv("GEMINI_API_KEY"));
        String model = System.getenv("GEMINI_MODEL");
        if (model != null && !model.isBlank()) properties.setModel(model);
        try (var generator = new GeminiCriteriaGenerator(properties)) {
            var extractor = new SearchCriteriaExtractor(generator,
                    Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC));
            var result = extractor.extract("Tìm phim kinh dị ngày mai từ 19 giờ đến 22 giờ ở Thủ Đức");
            assertNull(result.movieTitle());
            assertEquals("kinh dị", result.genre().toLowerCase(java.util.Locale.ROOT));
            assertEquals(LocalDate.of(2026, 10, 29), result.date());
            assertEquals(LocalTime.of(19, 0), result.timeRange().start());
            assertEquals(LocalTime.of(22, 0), result.timeRange().end());
            assertEquals(SearchCriteria.LocationType.AREA, result.location().type());
            assertTrue(result.location().text().contains("Thủ Đức"));
        }
    }
}
