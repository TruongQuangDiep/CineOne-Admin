package com.example.cineone_admin.tmdb;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("tmdb")
public class TmdbProperties {
    private String accessToken = "";
    private String baseUrl = "https://api.themoviedb.org/3";
    private String imageBaseUrl = "https://image.tmdb.org/t/p/w500";
    private String language = "vi-VN";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration requestTimeout = Duration.ofSeconds(10);

    public void validate() {
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                || requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero())
            throw new IllegalArgumentException("TMDB timeout phải lớn hơn 0");
        if (baseUrl == null || baseUrl.isBlank() || imageBaseUrl == null || imageBaseUrl.isBlank()
                || language == null || language.isBlank())
            throw new IllegalArgumentException("TMDB URL và ngôn ngữ không được để trống");
    }
}
