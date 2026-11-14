package com.example.cineone_admin.controller;

import com.example.cineone_admin.dto.voucher.VoucherRequest;
import com.example.cineone_admin.repository.MembershipTierRepository;
import com.example.cineone_admin.service.VoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;
    private final MembershipTierRepository membershipTierRepository;

    @GetMapping
    public String listVouchers(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Model model) {

        var pageable = PageRequest.of(page, size, Sort.by("voucherId").descending());
        var vouchersPage = voucherService.getVouchers(keyword, pageable);

        model.addAttribute("activeMenu", "vouchers");
        model.addAttribute("vouchersPage", vouchersPage);
        model.addAttribute("keyword", keyword);
        return "vouchers/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        var allTiers = membershipTierRepository.findAll();
        VoucherRequest req = new VoucherRequest();
        // Mặc định chọn tất cả các hạng khi tạo mới
        req.setMembershipTierIds(allTiers.stream().map(com.example.cineone_admin.entity.MembershipTier::getMembershipTierId).toList());

        model.addAttribute("activeMenu", "vouchers");
        model.addAttribute("voucherRequest", req);
        model.addAttribute("allTiers", allTiers);
        model.addAttribute("isEdit", false);
        return "vouchers/form";
    }

    @PostMapping("/create")
    public String handleCreate(
            @Valid @ModelAttribute("voucherRequest") VoucherRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("activeMenu", "vouchers");
            model.addAttribute("isEdit", false);
            return "vouchers/form";
        }
        try {
            voucherService.createVoucher(request);
            redirectAttributes.addFlashAttribute("successMessage", "Tạo voucher mới thành công!");
            return "redirect:/vouchers";
        } catch (Exception e) {
            model.addAttribute("activeMenu", "vouchers");
            model.addAttribute("isEdit", false);
            model.addAttribute("errorMessage", e.getMessage());
            return "vouchers/form";
        }
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable("id") Long id, Model model) {
        var v = voucherService.getVoucherEntity(id);
        VoucherRequest req = new VoucherRequest();
        req.setCode(v.getCode());
        req.setDescription(v.getDescription());
        req.setImageUrl(v.getImageUrl());
        req.setDiscountType(v.getDiscountType());
        req.setDiscountValue(v.getDiscountValue());
        req.setMinOrder(v.getMinOrder());
        req.setMaxDiscount(v.getMaxDiscount());
        req.setUsageLimit(v.getUsageLimit());
        req.setStartAt(v.getStartAt());
        req.setEndAt(v.getEndAt());
        req.setStatus(v.getStatus());
        req.setMembershipTierIds(voucherService.getApplicableTierIds(id));

        model.addAttribute("activeMenu", "vouchers");
        model.addAttribute("voucherRequest", req);
        model.addAttribute("allTiers", membershipTierRepository.findAll());
        model.addAttribute("voucherId", id);
        model.addAttribute("isEdit", true);
        return "vouchers/form";
    }

    @PostMapping("/edit/{id}")
    public String handleUpdate(
            @PathVariable("id") Long id,
            @Valid @ModelAttribute("voucherRequest") VoucherRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("activeMenu", "vouchers");
            model.addAttribute("isEdit", true);
            model.addAttribute("voucherId", id);
            return "vouchers/form";
        }
        try {
            voucherService.updateVoucher(id, request);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật voucher thành công!");
            return "redirect:/vouchers";
        } catch (Exception e) {
            model.addAttribute("activeMenu", "vouchers");
            model.addAttribute("isEdit", true);
            model.addAttribute("voucherId", id);
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("allTiers", membershipTierRepository.findAll());
            return "vouchers/form";
        }
    }

    @PostMapping("/{id}/toggle")
    public String toggleStatus(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            voucherService.toggleVoucherStatus(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật trạng thái của voucher!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/vouchers";
    }
}