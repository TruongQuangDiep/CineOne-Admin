package com.example.cineone_admin.sync.mock;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.core.io.ResourceLoader;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

public final class MockSourceReader {
    private final ResourceLoader resources;
    private final String root;
    private final JsonMapper json = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    public MockSourceReader(ResourceLoader resources, String root) {
        this.resources = Objects.requireNonNull(resources);
        this.root = root.endsWith("/") ? root : root + "/";
    }

    public List<Map<String, String>> read(String provider, boolean isJson, String file, String array,
                                         Set<String> required) throws IOException {
        String location = root + provider.toLowerCase(Locale.ROOT) + "/" + file + (isJson ? ".json" : ".csv");
        try (var reader = new PushbackReader(new InputStreamReader(
                resources.getResource(location).getInputStream(), StandardCharsets.UTF_8), 1)) {
            int first = reader.read();
            if (first != -1 && first != '\uFEFF') reader.unread(first);
            return isJson ? readJson(reader, provider, array, required) : readCsv(reader, required);
        }
    }

    private List<Map<String, String>> readJson(Reader reader, String provider, String array,
                                               Set<String> required) {
        JsonNode document = json.readTree(reader);
        if (document == null || !provider.equals(document.path("provider_code").asString("")))
            throw new IllegalArgumentException("provider_code không khớp hãng đang đọc");
        JsonNode records = document.path(array);
        if (!records.isArray()) throw new IllegalArgumentException("Thiếu mảng " + array);
        List<Map<String, String>> result = new ArrayList<>();
        for (JsonNode node : records) {
            if (array.equals("templates")) {
                Map<String, String> template = fields(node, Set.of("template_code", "room_type", "seats"));
                if (!node.path("seats").isArray()) throw new IllegalArgumentException("seats phải là mảng");
                for (JsonNode seat : node.path("seats")) {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("template_code", template.get("template_code"));
                    row.put("room_type", template.get("room_type"));
                    Map<String, String> source = fields(seat, Set.of("row", "number", "type"));
                    row.put("seat_row", source.get("row"));
                    row.put("seat_number", source.get("number"));
                    row.put("seat_type", source.get("type"));
                    result.add(row);
                }
            } else {
                try { result.add(fields(node, required)); }
                catch (IllegalArgumentException ex) {
                    Map<String, String> invalid = new LinkedHashMap<>();
                    invalid.put("external_id", node.path("external_id").asString(""));
                    invalid.put("__record_error", ex.getMessage());
                    result.add(invalid);
                }
            }
        }
        return result;
    }

    private Map<String, String> fields(JsonNode node, Set<String> required) {
        if (!node.isObject()) throw new IllegalArgumentException("Bản ghi phải là object");
        Map<String, String> values = new LinkedHashMap<>();
        for (var entry : node.properties()) {
            JsonNode value = entry.getValue();
            if (value.isString() || value.isNumber()) values.put(entry.getKey(), value.asString());
            else if (value.isNull()) values.put(entry.getKey(), null);
            else if (required.contains(entry.getKey()) && !entry.getKey().equals("seats")
                    || entry.getKey().equals("status"))
                throw new IllegalArgumentException("Trường phải là chuỗi hoặc số: " + entry.getKey());
        }
        for (String field : required) {
            if (!node.has(field)) throw new IllegalArgumentException("Thiếu trường " + field);
        }
        return values;
    }

    private List<Map<String, String>> readCsv(Reader reader, Set<String> required) throws IOException {
        var format = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true).setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW).get();
        try (CSVParser parser = format.parse(reader)) {
            if (!parser.getHeaderNames().containsAll(required))
                throw new IllegalArgumentException("CSV thiếu cột bắt buộc: " + required);
            List<Map<String, String>> result = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (!record.isConsistent()) {
                    Map<String, String> invalid = new LinkedHashMap<>();
                    invalid.put("external_id", record.isSet("external_id") ? record.get("external_id") : "");
                    invalid.put("__record_error", "CSV sai số cột tại bản ghi " + record.getRecordNumber());
                    result.add(invalid);
                } else result.add(record.toMap());
            }
            return result;
        }
    }
}
