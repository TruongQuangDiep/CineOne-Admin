package com.example.cineone_admin.tmdb;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(TmdbProperties.class)
public class TmdbConfiguration {
    @Bean
    public TmdbClient tmdbClient(TmdbProperties properties) {
        properties.validate();
        var http = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var web = WebClient.builder().baseUrl(properties.getBaseUrl())
                .clientConnector(new JdkClientHttpConnector(http))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)).build();
        return new TmdbClient(web, properties);
    }
}
