package com.example.cineone_admin.controller;

import com.example.cineone_admin.dto.provider.ProviderRequest;
import com.example.cineone_admin.enums.ProviderEntityType;
import com.example.cineone_admin.service.ProviderService;
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
public class ProviderController {
    private final ProviderService service;

    @GetMapping("/providers")
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("providers", service.search(q, page));
        model.addAttribute("q", q);
        return "providers/list";
    }

    @GetMapping("/providers/new")
    public String create(Model model) {
        var request = new ProviderRequest();
        request.setMock(true);
        model.addAttribute("providerForm", request);
        return form(model, null);
    }

    @GetMapping("/providers/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        var provider = service.get(id);
        var form = new ProviderRequest();
        form.setCode(provider.getCode()); form.setName(provider.getName());
        form.setProviderType(provider.getProviderType()); form.setMock(Boolean.TRUE.equals(provider.getMock()));
        model.addAttribute("providerForm", form);
        return form(model, id);
    }

    @GetMapping("/providers/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("provider", service.get(id));
        return "providers/detail";
    }

    @PostMapping("/providers")
    public String saveNew(@Valid @ModelAttribute("providerForm") ProviderRequest request,
                          BindingResult errors, Model model, RedirectAttributes flash) {
        return save(null, request, errors, model, flash);
    }

    @PostMapping("/providers/{id}")
    public String saveExisting(@PathVariable Long id,
            @Valid @ModelAttribute("providerForm") ProviderRequest request,
            BindingResult errors, Model model, RedirectAttributes flash) {
        service.get(id);
        return save(id, request, errors, model, flash);
    }

    private String save(Long id, ProviderRequest request, BindingResult errors,
                        Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) return form(model, id);
        try {
            var provider = service.save(id, request);
            flash.addFlashAttribute("success", id == null ? "Đã thêm nhà cung cấp." : "Đã cập nhật nhà cung cấp.");
            return "redirect:/providers/" + provider.getProviderId();
        } catch (IllegalArgumentException ex) {
            errors.reject("provider", ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            errors.reject("provider", "Không thể lưu. Mã nhà cung cấp có thể đã tồn tại.");
        }
        return form(model, id);
    }

    private String form(Model model, Long id) {
        model.addAttribute("providerId", id);
        return "providers/form";
    }

    @PostMapping("/providers/{id}/status")
    public String status(@PathVariable Long id, @RequestParam boolean enabled, RedirectAttributes flash) {
        service.setEnabled(id, enabled);
        flash.addFlashAttribute("success", enabled ? "Đã bật nhà cung cấp. Có thể đồng bộ lại dữ liệu."
                : "Đã ngừng đồng bộ. Dữ liệu và các mã ánh xạ vẫn được giữ nguyên.");
        return "redirect:/providers";
    }

    @GetMapping("/mappings")
    public String mappings(@RequestParam(required = false) Long providerId,
            @RequestParam(required = false) ProviderEntityType entity,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "false") boolean includeStopped, Model model) {
        model.addAttribute("mappings", service.searchMappings(providerId, entity, status, q, page, includeStopped));
        model.addAttribute("providers", service.all());
        model.addAttribute("entities", ProviderEntityType.values());
        model.addAttribute("providerId", providerId); model.addAttribute("entity", entity);
        model.addAttribute("status", status); model.addAttribute("q", q);
        model.addAttribute("includeStopped", includeStopped);
        return "mappings/list";
    }

    @GetMapping("/mappings/{id}")
    public String mapping(@PathVariable Long id, Model model) {
        model.addAttribute("mapping", service.mapping(id));
        return "mappings/detail";
    }
}
