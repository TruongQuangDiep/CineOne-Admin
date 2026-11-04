package com.example.cineone_admin.controller.api;

import com.example.cineone_admin.dto.catalog.ShowtimeResponse;
import com.example.cineone_admin.entity.Showtime;
import com.example.cineone_admin.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import com.example.cineone_admin.dto.catalog.SeatResponse;
import com.example.cineone_admin.entity.Seat;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/showtimes")
@RequiredArgsConstructor
public class ShowtimeApiController {

    private final CatalogService catalogService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getShowtimes(
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long cinemaId,
            @RequestParam(defaultValue = "") String cinema,
            @RequestParam(defaultValue = "") String movie,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page) {

        Page<Showtime> showtimePage = catalogService.showtimes(movieId, cinemaId, cinema, movie, date, status, page);
        List<ShowtimeResponse> data = showtimePage.getContent().stream()
                .map(ShowtimeResponse::from)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("status", 200);
        response.put("message", "Lấy danh sách suất chiếu thành công.");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/seats")
    public ResponseEntity<Map<String, Object>> getShowtimeSeats(@PathVariable Long id) {
        List<Seat> seats = catalogService.seatsForShowtime(id);
        List<SeatResponse> data = seats.stream().map(SeatResponse::from).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("status", 200);
        response.put("message", "Lấy sơ đồ ghế thành công.");
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

