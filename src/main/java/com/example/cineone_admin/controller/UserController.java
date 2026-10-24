package com.example.cineone_admin.controller;

import com.example.cineone_admin.dto.user.UserRequest;
import com.example.cineone_admin.service.MembershipService;
import com.example.cineone_admin.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final MembershipService membershipService;

    @GetMapping("/users")
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(required = false) Long tierId,
                       @RequestParam(defaultValue = "") String status,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("users", userService.search(q, tierId, status, page));
        model.addAttribute("tiers", membershipService.all());
        model.addAttribute("q", q);
        model.addAttribute("tierId", tierId);
        model.addAttribute("status", status);
        return "users/list";
    }

    @GetMapping("/users/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("user", userService.get(id));
        return "users/detail";
    }

    @GetMapping("/users/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        var user = userService.get(id);
        var form = new UserRequest();
        form.setMembershipTierId(user.membershipTierId());
        model.addAttribute("userForm", form);
        model.addAttribute("user", user);
        return formView(model, id);
    }

    @PostMapping("/users/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("userForm") UserRequest request,
                         BindingResult errors, Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            model.addAttribute("user", userService.get(id));
            return formView(model, id);
        }
        try {
            userService.update(id, request);
            flash.addFlashAttribute("success", "Đã cập nhật hạng thành viên cho người dùng.");
            return "redirect:/users/" + id;
        } catch (IllegalArgumentException ex) {
            errors.reject("user", ex.getMessage());
        }
        model.addAttribute("user", userService.get(id));
        return formView(model, id);
    }

    private String formView(Model model, Long id) {
        model.addAttribute("userId", id);
        model.addAttribute("tiers", membershipService.all());
        return "users/form";
    }

    @PostMapping("/users/{id}/status")
    public String setStatus(@PathVariable Long id, @RequestParam String status, RedirectAttributes flash) {
        userService.setStatus(id, status);
        flash.addFlashAttribute("success", "LOCKED".equals(status) ? "Đã khóa tài khoản người dùng." : "Đã cập nhật trạng thái tài khoản.");
        return "redirect:/users/" + id;
    }
}