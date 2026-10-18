package com.example.cineone_admin.tmdb;

import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

/** Client chỉ đọc TMDB. Gọi từ luồng MVC/batch, không gọi từ event loop reactive. */
public class TmdbClient {
    public enum MovieList {
        NOW_PLAYING("now_playing"), UPCOMING("upcoming"), POPULAR("popular"), TOP_RATED("top_rated");
        private final String path;
        MovieList(String path) { this.path = path; }
    }
    private final WebClient web;
    private final TmdbProperties properties;

    public TmdbClient(WebClient web, TmdbProperties properties) {
        this.web = web;
        this.properties = properties;
    }

    public TmdbDtos.MovieDetail details(long id) {
        if (id <= 0) throw new IllegalArgumentException("TMDB ID phải lớn hơn 0");
        var detail = get("/movie/" + id, Map.of("append_to_response", "credits,videos"), TmdbDtos.MovieDetail.class);
        if (detail.id() == null || detail.id() != id) throw invalid();
        return detail;
    }

    public TmdbDtos.Genres genres() {
        var result = get("/genre/movie/list", Map.of(), TmdbDtos.Genres.class);
        if (result.genres() == null) throw invalid();
        return result;
    }

    public TmdbDtos.Page movies(MovieList list, int page) {
        if (list == null) throw new IllegalArgumentException("Phải chọn danh sách phim");
        checkPage(page);
        return page("/movie/" + list.path, Map.of("page", page), page);
    }

    public TmdbDtos.Page search(String query, int page) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("Từ khóa không được trống");
        checkPage(page);
        return page("/search/movie", Map.of("page", page, "query", query.trim(), "include_adult", false), page);
    }

    private TmdbDtos.Page page(String path, Map<String, Object> params, int requested) {
        var result = get(path, params, TmdbDtos.Page.class);
        if (result.page() == null || result.page() != requested || result.totalPages() == null
                || result.totalPages() < 0 || result.totalResults() == null || result.totalResults() < 0
                || result.results() == null || result.results().stream().anyMatch(m -> m == null || m.id() == null || m.id() <= 0))
            throw invalid();
        return result;
    }

    private void checkPage(int page) {
        if (page < 1 || page > 500) throw new IllegalArgumentException("Trang TMDB phải từ 1 đến 500");
    }

    private <T> T get(String path, Map<String, Object> params, Class<T> type) {
        if (properties.getAccessToken() == null || properties.getAccessToken().isBlank())
            throw new TmdbException(TmdbException.Kind.CONFIGURATION, "Chưa cấu hình TMDB_ACCESS_TOKEN");
        return web.get().uri(builder -> {
                    builder.path(path).queryParam("language", properties.getLanguage());
                    params.forEach((key, value) -> builder.queryParam(key, "{" + key + "}"));
                    return builder.build(params);
                })
                .headers(h -> h.setBearerAuth(properties.getAccessToken()))
                .exchangeToMono(response -> {
                    if (!response.statusCode().is2xxSuccessful()) {
                        int status = response.statusCode().value();
                        return response.releaseBody().then(Mono.<T>error(new TmdbException(
                                TmdbException.Kind.HTTP, "TMDB trả lỗi HTTP " + status, status)));
                    }
                    return response.bodyToMono(type).switchIfEmpty(Mono.error(invalid()));
                })
                // Giới hạn toàn bộ yêu cầu, gồm kết nối, chờ headers và đọc body.
                .timeout(properties.getRequestTimeout())
                .onErrorMap(error -> {
                    if (error instanceof TmdbException) return error;
                    for (Throwable cause = error; cause != null; cause = cause.getCause())
                        if (cause instanceof TimeoutException || cause instanceof HttpTimeoutException)
                            return new TmdbException(TmdbException.Kind.TIMEOUT, "TMDB phản hồi quá thời gian cho phép");
                    if (error instanceof WebClientRequestException)
                        return new TmdbException(TmdbException.Kind.NETWORK, "Không thể kết nối TMDB");
                    return invalid();
                }).block();
    }

    private TmdbException invalid() {
        return new TmdbException(TmdbException.Kind.INVALID_RESPONSE, "Dữ liệu TMDB trả về không hợp lệ");
    }
}
