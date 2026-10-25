package com.example.cineone_admin.controller;

import com.example.cineone_admin.dto.knowledgebase.KnowledgeBaseRequest;
import com.example.cineone_admin.entity.KnowledgeBaseEntry;
import com.example.cineone_admin.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/knowledgebase")
public class KnowledgeBaseController {

    private final KnowledgeBaseService service;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("entries", service.search(keyword, category, status, page, 10));
        model.addAttribute("categories", KnowledgeBaseService.DEFAULT_CATEGORIES);
        model.addAttribute("keyword", keyword);
        model.addAttribute("category", category);
        model.addAttribute("status", status);
        return "knowledgebase/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("entry", new KnowledgeBaseRequest());
        model.addAttribute("categories", KnowledgeBaseService.DEFAULT_CATEGORIES);
        model.addAttribute("isEdit", false);
        return "knowledgebase/form";
    }

    @PostMapping("/create")
    public String create(@Valid @ModelAttribute("entry") KnowledgeBaseRequest request,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", KnowledgeBaseService.DEFAULT_CATEGORIES);
            model.addAttribute("isEdit", false);
            return "knowledgebase/form";
        }
        try {
            service.create(request);
            redirectAttributes.addFlashAttribute("successMessage", "Thêm bài viết kiến thức mới thành công.");
            return "redirect:/knowledgebase";
        } catch (ResponseStatusException ex) {
            model.addAttribute("errorMessage", ex.getReason());
            model.addAttribute("categories", KnowledgeBaseService.DEFAULT_CATEGORIES);
            model.addAttribute("isEdit", false);
            return "knowledgebase/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        KnowledgeBaseEntry existing = service.getById(id);
        KnowledgeBaseRequest request = KnowledgeBaseRequest.builder()
                .title(existing.getTitle())
                .category(existing.getCategory())
                .content(existing.getContent())
                .status(existing.getStatus())
                .build();

        model.addAttribute("entry", request);
        model.addAttribute("id", id);
        model.addAttribute("categories", KnowledgeBaseService.DEFAULT_CATEGORIES);
        model.addAttribute("isEdit", true);
        return "knowledgebase/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("entry") KnowledgeBaseRequest request,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("id", id);
            model.addAttribute("categories", KnowledgeBaseService.DEFAULT_CATEGORIES);
            model.addAttribute("isEdit", true);
            return "knowledgebase/form";
        }
        try {
            service.update(id, request);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật bài viết thành công.");
            return "redirect:/knowledgebase";
        } catch (ResponseStatusException ex) {
            model.addAttribute("errorMessage", ex.getReason());
            model.addAttribute("id", id);
            model.addAttribute("categories", KnowledgeBaseService.DEFAULT_CATEGORIES);
            model.addAttribute("isEdit", true);
            return "knowledgebase/form";
        }
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        service.toggleStatus(id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã thay đổi trạng thái bài viết.");
        return "redirect:/knowledgebase";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        service.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa bài viết khỏi cơ sở tri thức.");
        return "redirect:/knowledgebase";
    }
}