package com.example.cineone_admin.service;

import com.example.cineone_admin.exception.AiExtractionException;
import com.example.cineone_admin.dto.ai.SearchCriteria;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
public final class CriteriaParser {
    private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final SearchInputGuard privacy = new SearchInputGuard();

    public SearchCriteria parse(String json) {
        try {
            if (json == null || json.isBlank() || json.length() > 8192) throw invalid();
            JsonNode root = mapper.readTree(json);
            keys(root, "movie_title", "genre", "date", "time_range", "location");
            String movie = text(root.get("movie_title"), 255);
            String genre = text(root.get("genre"), 100);
            String dateText = text(root.get("date"), 10);
            LocalDate date = null;
            if (dateText != null) {
                if (!dateText.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid();
                date = LocalDate.parse(dateText);
            }
            SearchCriteria.TimeRange time = null;
            JsonNode range = root.get("time_range");
            if (!range.isNull()) {
                keys(range, "expression", "start", "end");
                String expression = text(range.get("expression"), 150);
                if (expression == null) throw invalid();
                time = new SearchCriteria.TimeRange(expression,
                        time(range.get("start")), time(range.get("end")));
            }
            SearchCriteria.Location location = null;
            JsonNode loc = root.get("location");
            if (!loc.isNull()) {
                keys(loc, "type", "text");
                var type = SearchCriteria.LocationType.valueOf(text(loc.get("type"), 20));
                String value = text(loc.get("text"), 255);
                boolean privateReference = type == SearchCriteria.LocationType.NEARBY
                        || type == SearchCriteria.LocationType.HOME;
                if (privateReference != (value == null)) throw invalid();
                location = new SearchCriteria.Location(type, value);
            }
            return new SearchCriteria(movie, genre, date, time, location);
        } catch (RuntimeException e) {
            throw invalid();
        }
    }

    private static void keys(JsonNode node, String... expected) {
        if (node == null || !node.isObject()) throw invalid();
        Set<String> actual = new HashSet<>();
        node.propertyNames().forEach(actual::add);
        if (!actual.equals(Set.of(expected))) throw invalid();
    }

    private String text(JsonNode node, int max) {
        if (node == null) throw invalid();
        if (node.isNull()) return null;
        if (!node.isString()) throw invalid();
        String value = node.asString();
        if (value.isBlank() || value.length() > max) throw invalid();
        return privacy.validate(value);
    }

    private LocalTime time(JsonNode node) {
        String value = text(node, 5);
        if (value == null) return null;
        if (!value.matches("[0-9]{2}:[0-9]{2}")) throw invalid();
        return LocalTime.parse(value);
    }

    private static AiExtractionException invalid() {
        return new AiExtractionException(AiExtractionException.Kind.INVALID_RESPONSE);
    }
}
