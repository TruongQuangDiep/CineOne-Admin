package com.example.cineone_admin.controller;

import com.example.cineone_admin.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @GetMapping("/reviews")
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(required = false) Byte rating,
                       @RequestParam(defaultValue = "") String status,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("reviews", reviewService.search(q, rating, status, page));
        model.addAttribute("q", q);
        model.addAttribute("rating", rating);
        model.addAttribute("status", status);
        return "reviews/list";
    }

    @PostMapping("/reviews/{id}/status")
    public String toggleStatus(@PathVariable Long id, @RequestParam boolean visible, RedirectAttributes flash) {
        reviewService.toggleStatus(id, visible);
        flash.addFlashAttribute("success", visible ? "Đã duyệt và hiển thị bài đánh giá." : "Đã ẩn bài đánh giá vi phạm.");
        return "redirect:/reviews";
    }
}