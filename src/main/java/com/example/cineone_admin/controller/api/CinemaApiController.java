package com.example.cineone_admin.controller.api;

import com.example.cineone_admin.dto.catalog.CinemaResponse;
import com.example.cineone_admin.dto.catalog.FnbResponse;
import com.example.cineone_admin.entity.FnbItem;
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
@RequestMapping("/api/v1/cinemas")
@RequiredArgsConstructor
public class CinemaApiController {

    private final CatalogService catalogService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getCinemas(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String city,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(required = false) Long providerId,
            @RequestParam(defaultValue = "0") int page) {

        Page<CatalogService.CinemaRow> cinemaPage = catalogService.cinemas(q, city, providerId, status, page);
        List<CinemaResponse> data = cinemaPage.getContent().stream()
                .map(CinemaResponse::from)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("status", 200);
        response.put("message", "Lấy danh sách rạp thành công.");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getCinemaDetail(@PathVariable Long id) {
        CatalogService.CinemaRow row = catalogService.cinemaWithRoomCount(id);
        CinemaResponse data = CinemaResponse.from(row);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("status", 200);
        response.put("message", "Lấy chi tiết rạp thành công.");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/fnbs")
    public ResponseEntity<Map<String, Object>> getCinemaFnbs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page) {

        Page<FnbItem> fnbPage = catalogService.fnb(q, "", id, status, page);
        List<FnbResponse> data = fnbPage.getContent().stream()
                .map(FnbResponse::from)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("status", 200);
        response.put("message", "Lấy danh sách F&B thành công.");
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

