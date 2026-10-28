package com.example.cineone_admin.service;

import com.example.cineone_admin.config.GeminiProperties;
import com.example.cineone_admin.config.SearchTimeProperties;

import java.io.IOException;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;
import tools.jackson.databind.json.JsonMapper;
@EnabledIfEnvironmentVariable(named = "CINEONE_GEMINI_MANUAL_TEST", matches = "true")
class GeminiManualTest {
    @Test
    void printsExtractedCriteria() throws IOException {
        var properties = new GeminiProperties();
        properties.setApiKey(System.getenv("GEMINI_API_KEY"));
        String model = System.getenv("GEMINI_MODEL");
        if (model != null && !model.isBlank()) properties.setModel(model);

        var environment = new StandardEnvironment();
        environment.getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
        var timeProperties = Binder.get(environment).bind("ai.search.time", SearchTimeProperties.class)
                .orElseGet(SearchTimeProperties::new);
        var extractor = new SearchCriteriaExtractor(
                new GeminiCriteriaGenerator(properties), Clock.systemUTC(), timeProperties);
        var result = extractor.extract(System.getenv("CINEONE_GEMINI_INPUT"));
        System.out.println("\n--- Extracted criteria (Vietnam reference date: today) ---");
        System.out.println(new JsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }
}
