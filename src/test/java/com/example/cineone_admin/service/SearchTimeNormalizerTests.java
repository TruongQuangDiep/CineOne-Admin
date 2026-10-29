package com.example.cineone_admin.service;

import com.example.cineone_admin.config.SearchTimeProperties;
import com.example.cineone_admin.dto.ai.SearchCriteria;
import com.example.cineone_admin.dto.ai.SearchCriteria.TimeRange;
import com.example.cineone_admin.exception.AiExtractionException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class SearchTimeNormalizerTests {
    private final SearchTimeProperties properties = new SearchTimeProperties();
    private final SearchTimeNormalizer normalizer = new SearchTimeNormalizer(properties);

    @ParameterizedTest
    @CsvSource({
            "khoảng 20 giờ,19:30,20:30,0,0",
            "tầm 8h tối,19:30,20:30,0,0",
            "tầm 8 giờ rưỡi tối,20:00,21:00,0,0",
            "khoảng 20 giờ ngày mai,19:30,20:30,0,0",
            "khoang 20h30,20:00,21:00,0,0",
            "khoảng 08:15,07:45,08:45,0,0",
            "khoảng 0 giờ,23:30,00:30,-1,0",
            "khoảng 23:45,23:15,00:15,0,1",
            "khoảng 12 giờ đêm,23:30,00:30,-1,0"
    })
    void approximateTimesHaveDeterministicBoundsAndCorrectDates(String expression, String start,
            String end, int startOffset, int endOffset) {
        var result = normalize(expression);
        assertEquals(LocalTime.parse(start), result.start());
        assertEquals(LocalTime.parse(end), result.end());
        assertEquals(startOffset, result.startDayOffset());
        assertEquals(endOffset, result.endDayOffset());
        assertTrue(result.startInclusive());
        assertTrue(result.endInclusive());
        assertFalse(result.requiresClarification());
        assertEquals(expression, result.expression());
    }

    @ParameterizedTest
    @CsvSource({
            "buổi sáng,06:00,12:00,0", "chiều,12:00,18:00,0",
            "vào buổi tối,18:00,00:00,1", "buổi khuya,00:00,06:00,0",
            "tối mai,18:00,00:00,1", "chiều nay,12:00,18:00,0"
    })
    void periodsUseConfiguredHalfOpenIntervals(String expression, String start, String end, int endOffset) {
        var result = normalize(expression);
        assertEquals(LocalTime.parse(start), result.start());
        assertEquals(LocalTime.parse(end), result.end());
        assertEquals(endOffset, result.endDayOffset());
        assertTrue(result.startInclusive());
        assertFalse(result.endInclusive());
        assertFalse(result.requiresClarification());
    }

    @ParameterizedTest
    @ValueSource(strings = {"8 giờ", "khoảng 8h", "khoảng 25 giờ", "khoảng 20:75",
            "khoảng 20 giờ sáng", "1 giờ tối", "20 giờ hoặc 21 giờ", "buổi chiều hoặc tối"})
    void ambiguousOrConflictingPhrasesCannotUseModelGuesses(String expression) {
        var result = normalizer.normalize(criteria(new TimeRange(expression,
                LocalTime.of(8, 0), LocalTime.of(9, 0)))).timeRange();
        assertNull(result.start());
        assertNull(result.end());
        assertNull(result.startDayOffset());
        assertNull(result.endDayOffset());
        assertTrue(result.requiresClarification());
    }

    @ParameterizedTest
    @CsvSource({
            "lúc 20 giờ,20:00,20:00,0,true,true",
            "8 giờ tối,20:00,20:00,0,true,true",
            "từ 19 giờ đến 22 giờ,19:00,22:00,0,true,false",
            "từ 7 giờ đến 9 giờ tối,19:00,21:00,0,true,false",
            "từ 22 giờ đến 1 giờ sáng hôm sau,22:00,01:00,1,true,false",
            "từ 19 giờ đến 20 giờ hôm sau,19:00,20:00,1,true,false"
    })
    void explicitTimesAndCrossMidnightRangesAreReadyForConsumers(String expression, String start,
            String end, int endOffset, boolean startInclusive, boolean endInclusive) {
        var result = normalize(expression);
        assertEquals(LocalTime.parse(start), result.start());
        assertEquals(LocalTime.parse(end), result.end());
        assertEquals(endOffset, result.endDayOffset());
        assertEquals(startInclusive, result.startInclusive());
        assertEquals(endInclusive, result.endInclusive());
        assertFalse(result.requiresClarification());
    }

    @Test void openBoundsKeepStrictnessAndNoInventedOtherBound() {
        var after = normalize("sau 19 giờ");
        assertEquals(LocalTime.of(19, 0), after.start());
        assertFalse(after.startInclusive());
        assertNull(after.end());
        assertNull(after.endDayOffset());
        var before = normalize("trước 22 giờ");
        assertEquals(LocalTime.of(22, 0), before.end());
        assertFalse(before.endInclusive());
        assertNull(before.start());
    }

    @Test void customRulesOverrideEvenModelSuppliedApproximateBounds() {
        properties.setApproximateMinutes(45);
        var result = normalizer.normalize(criteria(new TimeRange("khoảng 20 giờ",
                LocalTime.of(19, 0), LocalTime.of(21, 0)))).timeRange();
        assertEquals(LocalTime.of(19, 15), result.start());
        assertEquals(LocalTime.of(20, 45), result.end());
        properties.setEveningStart(LocalTime.of(17, 30));
        assertEquals(LocalTime.of(17, 30), normalize("buổi tối").start());
    }

    @Test void missingTimeAndUnrecognizedExpressionsDoNotInventFilters() {
        var empty = criteria(null);
        assertSame(empty, normalizer.normalize(empty));
        assertTrue(normalize("khi nào rảnh").requiresClarification());
    }

    @Test void lessCommonExplicitPhrasesRetainParsedBoundsWithRangeSemantics() {
        var result = normalizer.normalize(criteria(new TimeRange("19 đến 22 giờ",
                LocalTime.of(19, 0), LocalTime.of(22, 0)))).timeRange();
        assertEquals(LocalTime.of(19, 0), result.start());
        assertEquals(LocalTime.of(22, 0), result.end());
        assertTrue(result.startInclusive());
        assertFalse(result.endInclusive());
    }

    @Test void extractorNormalizesAndSerializesMetadataWithoutChangingDateOrFiveTopLevelKeys() {
        var clock = Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC);
        var extractor = new SearchCriteriaExtractor((s, u, schema) -> """
                {"movie_title":null,"genre":"kinh dị","date":"2026-10-29",
                 "time_range":{"expression":"buổi tối","start":null,"end":null},"location":null}
                """, clock);
        var result = extractor.extract("phim kinh dị tối mai");
        assertEquals(LocalDate.of(2026, 10, 29), result.date());
        var tree = new JsonMapper().readTree(new JsonMapper().writeValueAsString(result));
        assertEquals(5, tree.size());
        assertEquals("18:00", tree.at("/time_range/start").asString());
        assertEquals(1, tree.at("/time_range/end_day_offset").asInt());
        assertFalse(tree.at("/time_range/end_inclusive").asBoolean());
        assertFalse(tree.at("/time_range/requires_clarification").asBoolean());
    }

    @Test void invalidProductConfigurationFailsBeforeExternalCall() {
        var calls = new AtomicInteger();
        properties.setApproximateMinutes(0);
        var extractor = new SearchCriteriaExtractor((s, u, schema) -> {
            calls.incrementAndGet();
            return CriteriaParserTests.EMPTY;
        }, Clock.systemUTC(), properties);
        var error = assertThrows(AiExtractionException.class, () -> extractor.extract("phim ma"));
        assertEquals(AiExtractionException.Kind.CONFIGURATION, error.getKind());
        assertEquals(0, calls.get());
        properties.setApproximateMinutes(30);
        properties.setEveningEnd(properties.getEveningStart());
        assertThrows(AiExtractionException.class, normalizer::validateConfiguration);
    }

    private TimeRange normalize(String expression) {
        return normalizer.normalize(criteria(new TimeRange(expression, null, null))).timeRange();
    }

    private SearchCriteria criteria(TimeRange time) {
        return new SearchCriteria(null, null, null, time, null);
    }
}
