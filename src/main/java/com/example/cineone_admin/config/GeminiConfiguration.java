package com.example.cineone_admin.config;

import com.example.cineone_admin.service.CriteriaGenerator;
import com.example.cineone_admin.service.GeminiCriteriaGenerator;
import com.example.cineone_admin.service.SearchCriteriaExtractor;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({GeminiProperties.class, SearchTimeProperties.class})
public class GeminiConfiguration {
    @Bean(destroyMethod = "close")
    public GeminiCriteriaGenerator criteriaGenerator(GeminiProperties properties) {
        return new GeminiCriteriaGenerator(properties);
    }

    @Bean
    public SearchCriteriaExtractor searchCriteriaExtractor(CriteriaGenerator generator,
                                                          SearchTimeProperties timeProperties) {
        return new SearchCriteriaExtractor(generator, Clock.systemUTC(), timeProperties);
    }
}
