package com.example.cineone_admin.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;
public final class CriteriaContract {
    private final String systemPrompt;
    private final Map<String, Object> schema;

    @SuppressWarnings("unchecked")
    public CriteriaContract() {
        try {
            systemPrompt = read("ai/search-system-prompt.txt");
            schema = new JsonMapper().readValue(read("ai/search-criteria.schema.json"), Map.class);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Không thể đọc hợp đồng bóc tách AI.");
        }
    }

    private static String read(String path) throws IOException {
        try (var stream = new ClassPathResource(path).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public String systemPrompt() { return systemPrompt; }
    public Map<String, Object> schema() { return schema; }
}
