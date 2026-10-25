package com.example.cineone_admin.controller;

import com.example.cineone_admin.service.SyncHistoryService;
import com.example.cineone_admin.sync.ManualSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/sync-logs")
public class SyncHistoryController {
    private final SyncHistoryService history;
    private final ManualSyncService manual;

    @GetMapping
    public String list(@RequestParam(required = false) Long providerId,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("logs", history.search(providerId, status, fromDate, toDate, page));
        model.addAttribute("providers", history.options());
        model.addAttribute("providerId", providerId); model.addAttribute("status", status);
        model.addAttribute("fromDate", fromDate); model.addAttribute("toDate", toDate);
        return "sync/list";
    }

    @PostMapping("/start")
    @ResponseBody
    public ResponseEntity<Map<String, Long>> start(@RequestParam Long providerId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("id", manual.start(providerId)));
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<SyncHistoryService.LogRow> get(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(history.get(id));
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> businessError(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",
                ex.getReason() == null ? "Không thể thực hiện thao tác." : ex.getReason()));
    }
}
