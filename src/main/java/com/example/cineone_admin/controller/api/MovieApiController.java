package com.example.cineone_admin.controller.api;

import com.example.cineone_admin.dto.catalog.MovieDetailResponse;
import com.example.cineone_admin.dto.catalog.MovieResponse;
import com.example.cineone_admin.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/movies")
@RequiredArgsConstructor
public class MovieApiController {

    private final CatalogService catalogService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMovies(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(required = false) Long genreId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<CatalogService.MovieRow> moviePage = catalogService.movies(q, status, genreId, page, size);
        List<Long> movieIds = moviePage.getContent().stream()
                .map(r -> r.movie().getMovieId())
                .toList();
        Map<Long, Double> ratings = catalogService.ratingsFor(movieIds);

        List<MovieResponse> data = moviePage.getContent().stream()
                .map(row -> MovieResponse.from(row, ratings.getOrDefault(row.movie().getMovieId(), 0.0)))
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("status", 200);
        response.put("message", "Lấy danh sách phim thành công.");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getMovieDetail(@PathVariable Long id) {
        CatalogService.MovieRow row = catalogService.movie(id);
        Double rating = catalogService.ratingsFor(List.of(id)).getOrDefault(id, 0.0);
        MovieDetailResponse data = MovieDetailResponse.from(row, rating);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("status", 200);
        response.put("message", "Lấy chi tiết phim thành công.");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        int statusCode = ex.getStatusCode().value();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("status", statusCode);
        response.put("message", ex.getReason() != null ? ex.getReason() : "Đã xảy ra lỗi.");
        response.put("data", null);
        return ResponseEntity.status(statusCode).body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("status", 400);
        response.put("message", "Tham số truy vấn không hợp lệ: " + ex.getName());
        response.put("data", null);
        return ResponseEntity.badRequest().body(response);
    }
}

