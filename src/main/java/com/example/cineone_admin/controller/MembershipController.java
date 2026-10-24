package com.example.cineone_admin.controller;

import com.example.cineone_admin.dto.membership.MembershipTierRequest;
import com.example.cineone_admin.service.MembershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class MembershipController {
    private final MembershipService service;

    @GetMapping("/memberships")
    public String list(Model model) {
        model.addAttribute("tiers", service.all());
        return "memberships/list";
    }

    @GetMapping("/memberships/new")
    public String create(Model model) {
        model.addAttribute("tierForm", new MembershipTierRequest());
        return form(model, null);
    }

    @GetMapping("/memberships/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        var tier = service.get(id);
        var req = new MembershipTierRequest();
        req.setName(tier.getName());
        req.setMinSpending(tier.getMinSpending());
        req.setDescription(tier.getDescription());
        req.setStatus(tier.getStatus());
        model.addAttribute("tierForm", req);
        return form(model, id);
    }

    @PostMapping("/memberships")
    public String saveNew(@Valid @ModelAttribute("tierForm") MembershipTierRequest request,
                          BindingResult errors, Model model, RedirectAttributes flash) {
        return save(null, request, errors, model, flash);
    }

    @PostMapping("/memberships/{id}")
    public String saveExisting(@PathVariable Long id,
                               @Valid @ModelAttribute("tierForm") MembershipTierRequest request,
                               BindingResult errors, Model model, RedirectAttributes flash) {
        return save(id, request, errors, model, flash);
    }

    private String save(Long id, MembershipTierRequest request, BindingResult errors,
                        Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) return form(model, id);
        try {
            service.save(id, request);
            flash.addFlashAttribute("success", id == null ? "Đã thêm hạng thành viên mới." : "Đã cập nhật hạng thành viên.");
            return "redirect:/memberships";
        } catch (IllegalArgumentException ex) {
            errors.reject("tier", ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            errors.reject("tier", "Tên hạng thành viên có thể đã tồn tại.");
        }
        return form(model, id);
    }

    private String form(Model model, Long id) {
        model.addAttribute("tierId", id);
        return "memberships/form";
    }

    @PostMapping("/memberships/{id}/status")
    public String toggleStatus(@PathVariable Long id, @RequestParam boolean enabled, RedirectAttributes flash) {
        service.setStatus(id, enabled);
        flash.addFlashAttribute("success", enabled ? "Đã kích hoạt hạng thành viên." : "Đã vô hiệu hóa hạng thành viên.");
        return "redirect:/memberships";
    }
}