package com.example.cineone_admin.controller;

import com.example.cineone_admin.service.SupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/support")
public class SupportController {

    private final SupportService service;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String type,
                       @RequestParam(required = false) String category,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("requests", service.search(keyword, status, type, category, page, 10));
        model.addAttribute("statusList", SupportService.STATUS_OPTIONS);
        model.addAttribute("typeList", SupportService.TYPE_OPTIONS);
        model.addAttribute("categoryList", SupportService.CATEGORY_OPTIONS);
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("type", type);
        model.addAttribute("category", category);
        return "support/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("supportRequest", service.getById(id));
        model.addAttribute("statusList", SupportService.STATUS_OPTIONS);
        return "support/detail";
    }

    @PostMapping("/{id}/respond")
    public String respond(@PathVariable Long id,
                          @RequestParam String response,
                          @RequestParam String status,
                          RedirectAttributes redirectAttributes) {
        try {
            service.respond(id, response, status);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi phản hồi và cập nhật trạng thái yêu cầu hỗ trợ.");
        } catch (ResponseStatusException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getReason());
        }
        return "redirect:/support/" + id;
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam String status,
                               RedirectAttributes redirectAttributes) {
        try {
            service.updateStatus(id, status);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái thành công.");
        } catch (ResponseStatusException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getReason());
        }
        return "redirect:/support/" + id;
    }
}