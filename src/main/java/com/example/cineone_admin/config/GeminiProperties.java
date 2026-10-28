package com.example.cineone_admin.config;

import com.example.cineone_admin.exception.AiExtractionException;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("gemini")
public class GeminiProperties {
    private String apiKey = "";
    private String model = "gemini-3.5-flash-lite";
    private Duration requestTimeout = Duration.ofSeconds(15);

    public void validateForCall() {
        if (apiKey == null || apiKey.isBlank() || model == null
                || !model.matches("gemini-[a-zA-Z0-9.-]+")
                || requestTimeout == null || requestTimeout.toMillis() < 1
                || requestTimeout.toMillis() > 60_000) {
            throw new AiExtractionException(AiExtractionException.Kind.CONFIGURATION);
        }
    }
}
