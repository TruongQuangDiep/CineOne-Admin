package com.example.cineone_admin.controller.api;

import com.example.cineone_admin.dto.ticket.TicketResponse;
import com.example.cineone_admin.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketApiController {

    private final TicketService ticketService;

    @GetMapping("/my-tickets")
    public ResponseEntity<Map<String, Object>> getMyTickets(@RequestParam(defaultValue = "UPCOMING") String tab) {
        Map<String, Object> response = new HashMap<>();
        List<TicketResponse> data = ticketService.getMyTickets(tab);

        response.put("success", true);
        response.put("status", 200);
        response.put("message", "Lấy danh sách vé thành công.");
        response.put("data", data);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<Map<String, Object>> getTicketDetail(@PathVariable Long ticketId) {
        Map<String, Object> response = new HashMap<>();
        TicketResponse data = ticketService.getTicketDetail(ticketId);

        response.put("success", true);
        response.put("status", 200);
        response.put("message", "Lấy chi tiết vé thành công.");
        response.put("data", data);

        return ResponseEntity.ok(response);
    }
}