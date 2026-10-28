package com.example.cineone_admin.service;

import com.example.cineone_admin.exception.AiExtractionException;
import com.example.cineone_admin.dto.ai.SearchCriteria;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class CriteriaParserTests {
    static final String EMPTY = """
            {"movie_title":null,"genre":null,"date":null,"time_range":null,"location":null}
            """;
    private final CriteriaParser parser = new CriteriaParser();

    @Test void allowsAllFieldsToBeUnknownAndSerializesExactlyFiveKeys() {
        var result = parser.parse(EMPTY);
        assertNull(result.movieTitle());
        var tree = new JsonMapper().readTree(new JsonMapper().writeValueAsString(result));
        assertEquals(5, tree.size());
        assertTrue(tree.get("movie_title").isNull());
        assertTrue(tree.get("time_range").isNull());
    }

    @Test void parsesVietnameseAndExplicitTimesWithoutDatabaseOrInventedGenre() {
        var result = parser.parse("""
                {"movie_title":"Hộ Linh Tráng Sĩ","genre":null,"date":"2026-10-30",
                 "time_range":{"expression":"lúc 20 giờ","start":"20:00","end":"20:00"},
                 "location":{"type":"BRAND","text":"CGV Thủ Đức"}}
                """);
        assertEquals("Hộ Linh Tráng Sĩ", result.movieTitle());
        assertNull(result.genre());
        assertEquals(LocalDate.of(2026, 10, 30), result.date());
        assertEquals(LocalTime.of(20, 0), result.timeRange().start());
        assertEquals(result.timeRange().start(), result.timeRange().end());
        assertEquals(SearchCriteria.LocationType.BRAND, result.location().type());
        var serialized = new JsonMapper().readTree(new JsonMapper().writeValueAsString(result));
        assertEquals("20:00", serialized.at("/time_range/start").asString());
        assertEquals("2026-10-30", serialized.get("date").asString());
    }

    @Test void preservesPeriodAndApproximateExpressionsWithoutDefaultBounds() {
        for (String expression : new String[]{"buổi tối", "khoảng 20 giờ", "8 giờ"}) {
            var result = parser.parse(EMPTY.replace("\"time_range\":null",
                    "\"time_range\":{\"expression\":\"" + expression + "\",\"start\":null,\"end\":null}"));
            assertEquals(expression, result.timeRange().expression());
            assertNull(result.timeRange().start());
            assertNull(result.timeRange().end());
        }
    }

    @Test void acceptsCrossMidnightBoundsAndPrivateLocationReferenceWithoutCoordinates() {
        var result = parser.parse("""
                {"movie_title":null,"genre":"kinh dị","date":"2026-10-30",
                 "time_range":{"expression":"22 giờ đến 1 giờ sáng hôm sau","start":"22:00","end":"01:00"},
                 "location":{"type":"HOME","text":null}}
                """);
        assertTrue(result.timeRange().end().isBefore(result.timeRange().start()));
        assertNull(result.location().text());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "[]", "null", "```json\n{}\n```", "{bad-json}",
            "{\"movie_title\":true,\"genre\":null,\"date\":null,\"time_range\":null,\"location\":null}",
            "{\"movie_title\":null,\"genre\":null,\"date\":\"2026-02-30\",\"time_range\":null,\"location\":null}",
            "{\"movie_title\":null,\"genre\":null,\"date\":\"30/10/2026\",\"time_range\":null,\"location\":null}",
            "{\"movie_title\":null,\"genre\":null,\"date\":null,\"time_range\":null,\"location\":{\"type\":\"UNKNOWN\",\"text\":null}}",
            "{\"movie_title\":null,\"genre\":null,\"date\":null,\"time_range\":null,\"location\":{\"type\":\"NEARBY\",\"text\":\"10.123456,106.123456\"}}"
    })
    void rejectsMalformedOrSemanticallyInvalidResults(String input) {
        invalid(input);
    }

    @Test void rejectsDuplicatesExtraFieldsTrailingJsonAndWrongNestedKeys() {
        invalid(EMPTY.replace("\"genre\":null", "\"genre\":null,\"genre\":\"ma\""));
        invalid(EMPTY.replace("\"genre\":null", "\"genre\":null,\"sql\":\"DROP TABLE users\""));
        invalid(EMPTY + "{}");
        invalid(EMPTY.replace("\"time_range\":null", "\"time_range\":{\"expression\":\"tối\"}"));
        invalid(EMPTY.replace("\"time_range\":null",
                "\"time_range\":{\"expression\":\"25 giờ\",\"start\":\"25:00\",\"end\":null}"));
        invalid(EMPTY.replace("\"location\":null", "\"location\":{\"type\":\"AREA\",\"text\":null}"));
        invalid(EMPTY.replace("\"movie_title\":null", "\"movie_title\":\"abc@example.com\""));
        invalid(EMPTY.replace("\"genre\":null", "\"genre\":\"\""));
        invalid("a".repeat(8193));
    }

    private void invalid(String input) {
        var error = assertThrows(AiExtractionException.class, () -> parser.parse(input));
        assertEquals(AiExtractionException.Kind.INVALID_RESPONSE, error.getKind());
        assertNull(error.getCause());
        assertEquals("Dịch vụ AI trả về tiêu chí không hợp lệ.", error.getMessage());
    }
}
