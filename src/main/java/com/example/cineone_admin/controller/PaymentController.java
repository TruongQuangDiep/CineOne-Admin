package com.example.cineone_admin.controller;

import com.example.cineone_admin.enums.PaymentStatus;
import com.example.cineone_admin.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    public String listPayments(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Model model) {

        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var paymentsPage = paymentService.getPayments(keyword, status, pageable);

        model.addAttribute("activeMenu", "payments");
        model.addAttribute("paymentsPage", paymentsPage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("statuses", PaymentStatus.values());
        return "payments/list";
    }

    @GetMapping("/{id}")
    public String paymentDetail(@PathVariable("id") Long id, Model model) {
        var payment = paymentService.getPaymentEntity(id);
        model.addAttribute("activeMenu", "payments");
        model.addAttribute("payment", paymentService.mapToResponse(payment));
        model.addAttribute("paymentEntity", payment);
        model.addAttribute("statuses", PaymentStatus.values());
        return "payments/detail";
    }

    @PostMapping("/{id}/status")
    public String updateStatus(
            @PathVariable("id") Long id,
            @RequestParam("status") PaymentStatus status,
            RedirectAttributes redirectAttributes) {
        try {
            paymentService.updatePaymentStatus(id, status);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái thanh toán thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/payments/" + id;
    }
}