package com.example.cineone_admin.sync.mock;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class MockAdapterConfiguration {
    @Bean
    MockSourceReader mockSourceReader(ResourceLoader resources,
            @Value("${cineone.mock.source-root:classpath:mock/data_source/}") String root) {
        return new MockSourceReader(resources, root);
    }

    private MockCinemaAdapter adapter(MockSourceReader reader, String code, String name, boolean json) {
        return new MockCinemaAdapter(code, name, json, reader, Clock.system(ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    @Bean MockCinemaAdapter cgvAdapter(MockSourceReader r) { return adapter(r, "CGV", "CGV Cinemas Vietnam", true); }
    @Bean MockCinemaAdapter betaAdapter(MockSourceReader r) { return adapter(r, "BETA", "Beta Cinemas", true); }
    @Bean MockCinemaAdapter megaGsAdapter(MockSourceReader r) { return adapter(r, "MEGAGS", "Mega GS Cinemas", true); }
    @Bean MockCinemaAdapter galaxyAdapter(MockSourceReader r) { return adapter(r, "GALAXY", "Galaxy Cinema", false); }
    @Bean MockCinemaAdapter lotteAdapter(MockSourceReader r) { return adapter(r, "LOTTE", "Lotte Cinema", false); }
    @Bean MockCinemaAdapter cinestarAdapter(MockSourceReader r) { return adapter(r, "CINESTAR", "Cinestar", false); }
}
