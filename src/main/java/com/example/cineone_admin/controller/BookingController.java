package com.example.cineone_admin.controller;

import com.example.cineone_admin.dto.booking.UpdateBookingStatusRequest;
import com.example.cineone_admin.enums.BookingStatus;
import com.example.cineone_admin.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @GetMapping
    public String listBookings(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) BookingStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Model model) {

        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var bookingsPage = bookingService.getBookings(keyword, status, pageable);

        model.addAttribute("activeMenu", "bookings");
        model.addAttribute("bookingsPage", bookingsPage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("statuses", BookingStatus.values());
        return "bookings/list";
    }

    @GetMapping("/{id}")
    public String bookingDetail(@PathVariable("id") Long id, Model model) {
        var booking = bookingService.getBookingEntity(id);
        var bookingResponse = bookingService.mapToResponse(booking);

        model.addAttribute("activeMenu", "bookings");
        model.addAttribute("booking", bookingResponse);
        model.addAttribute("bookingEntity", booking);
        model.addAttribute("statuses", BookingStatus.values());
        return "bookings/detail";
    }

    @PostMapping("/{id}/status")
    public String updateStatus(
            @PathVariable("id") Long id,
            @ModelAttribute UpdateBookingStatusRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            bookingService.updateBookingStatus(id, request);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái đơn vé thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/bookings/" + id;
    }
}