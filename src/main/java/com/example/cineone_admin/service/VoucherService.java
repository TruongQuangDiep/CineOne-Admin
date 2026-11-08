package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.voucher.VoucherRequest;
import com.example.cineone_admin.dto.voucher.VoucherResponse;
import com.example.cineone_admin.entity.User;
import com.example.cineone_admin.entity.Voucher;
import com.example.cineone_admin.entity.VoucherUsage;
import com.example.cineone_admin.repository.UserRepository;
import com.example.cineone_admin.repository.VoucherRepository;
import com.example.cineone_admin.repository.VoucherUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<VoucherResponse> getVouchers(String keyword, Pageable pageable) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return voucherRepository.findByCodeContainingIgnoreCaseOrDescriptionContainingIgnoreCase(keyword.trim(), keyword.trim(), pageable)
                    .map(this::mapToResponse);
        }
        return voucherRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Lấy danh sách Voucher theo Tab cho Ví Voucher cá nhân (SCRUM-114):
     * - AVAILABLE: Khả dụng (Còn hạn, ACTIVE, User chưa dùng)
     * - USED: Đã sử dụng
     * - EXPIRED: Hết hạn
     */
    @Transactional(readOnly = true)
    public List<VoucherResponse> getMyVouchersMobileByTab(String tab) {
        User user = getAuthenticatedUser();
        LocalDateTime now = LocalDateTime.now();
        List<Voucher> allVouchers = voucherRepository.findAll();
        
        Set<Long> usedVoucherIds = new HashSet<>();
        if (user != null) {
            List<VoucherUsage> usages = voucherUsageRepository.findByUser_UserId(user.getUserId());
            for (VoucherUsage u : usages) {
                if (u.getVoucher() != null) {
                    usedVoucherIds.add(u.getVoucher().getVoucherId());
                }
            }
        }

        String filterTab = tab != null ? tab.trim().toUpperCase() : "AVAILABLE";
        List<VoucherResponse> result = new ArrayList<>();

        for (Voucher v : allVouchers) {
            boolean isUsed = usedVoucherIds.contains(v.getVoucherId());
            boolean isExpired = v.getEndAt() != null && v.getEndAt().isBefore(now);
            boolean isActive = "ACTIVE".equalsIgnoreCase(v.getStatus());

            if ("USED".equals(filterTab)) {
                if (isUsed) {
                    VoucherResponse res = mapToResponse(v);
                    res.setTab("USED");
                    result.add(res);
                }
            } else if ("EXPIRED".equals(filterTab)) {
                if (!isUsed && (isExpired || !isActive)) {
                    VoucherResponse res = mapToResponse(v);
                    res.setTab("EXPIRED");
                    result.add(res);
                }
            } else { // "AVAILABLE"
                if (!isUsed && !isExpired && isActive) {
                    VoucherResponse res = mapToResponse(v);
                    res.setTab("AVAILABLE");
                    result.add(res);
                }
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<VoucherResponse> getMyVouchersMobile() {
        return getMyVouchersMobileByTab("AVAILABLE");
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getMyVouchers() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("status", 200);
        response.put("message", "Lấy danh sách voucher thành công.");
        response.put("data", getMyVouchersMobile());
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy chi tiết Voucher theo Mã (cho màn hình Chi tiết voucher - Ảnh 4)
     */
    @Transactional(readOnly = true)
    public VoucherResponse getVoucherDetailByCode(String code) {
        Voucher v = voucherRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy voucher: " + code));
        return mapToResponse(v);
    }

    @Transactional(readOnly = true)
    public Voucher getVoucherEntity(Long id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy voucher với id: " + id));
    }

    @Transactional
    public VoucherResponse createVoucher(VoucherRequest request) {
        if (voucherRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Mã voucher đã tồn tại: " + request.getCode());
        }
        Voucher voucher = new Voucher();
        mapToEntity(voucher, request);
        return mapToResponse(voucherRepository.save(voucher));
    }

    @Transactional
    public VoucherResponse updateVoucher(Long id, VoucherRequest request) {
        Voucher voucher = getVoucherEntity(id);
        if (!voucher.getCode().equalsIgnoreCase(request.getCode()) && voucherRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Mã voucher đã tồn tại: " + request.getCode());
        }
        mapToEntity(voucher, request);
        return mapToResponse(voucherRepository.save(voucher));
    }

    @Transactional
    public void deleteVoucher(Long id) {
        voucherRepository.deleteById(id);
    }

    @Transactional
    public void toggleStatus(Long id) {
        Voucher voucher = getVoucherEntity(id);
        voucher.setStatus("ACTIVE".equals(voucher.getStatus()) ? "INACTIVE" : "ACTIVE");
        voucherRepository.save(voucher);
    }

    @Transactional
    public void toggleVoucherStatus(Long id) {
        toggleStatus(id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> applyVoucherForMobile(String code, long subtotal) {
        Map<String, Object> result = new HashMap<>();
        if (code == null || code.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "Vui lòng nhập mã giảm giá.");
            result.put("discountAmount", 0L);
            result.put("finalAmount", subtotal);
            return result;
        }

        var opt = voucherRepository.findByCodeIgnoreCase(code.trim());
        if (opt.isEmpty()) {
            result.put("success", false);
            result.put("message", "Mã voucher không tồn tại.");
            result.put("discountAmount", 0L);
            result.put("finalAmount", subtotal);
            return result;
        }

        Voucher v = opt.get();
        LocalDateTime now = LocalDateTime.now();
        if (!"ACTIVE".equalsIgnoreCase(v.getStatus())) {
            result.put("success", false);
            result.put("message", "Mã voucher đã hết hạn hoặc tạm khóa.");
            result.put("discountAmount", 0L);
            result.put("finalAmount", subtotal);
            return result;
        }

        if (v.getStartAt() != null && v.getStartAt().isAfter(now)) {
            result.put("success", false);
            result.put("message", "Chương trình ưu đãi chưa bắt đầu.");
            result.put("discountAmount", 0L);
            result.put("finalAmount", subtotal);
            return result;
        }

        if (v.getEndAt() != null && v.getEndAt().isBefore(now)) {
            result.put("success", false);
            result.put("message", "Mã voucher đã hết hạn sử dụng.");
            result.put("discountAmount", 0L);
            result.put("finalAmount", subtotal);
            return result;
        }

        if (v.getMinOrder() != null && subtotal < v.getMinOrder().longValue()) {
            result.put("success", false);
            result.put("message", "Đơn hàng tối thiểu phải từ " + String.format("%,d", v.getMinOrder().longValue()) + " đ");
            result.put("discountAmount", 0L);
            result.put("finalAmount", subtotal);
            return result;
        }

        long discount = 0;
        if ("PERCENT".equalsIgnoreCase(v.getDiscountType())) {
            discount = (long) (subtotal * (v.getDiscountValue().doubleValue() / 100.0));
            if (v.getMaxDiscount() != null && discount > v.getMaxDiscount().longValue()) {
                discount = v.getMaxDiscount().longValue();
            }
        } else {
            discount = v.getDiscountValue().longValue();
        }

        if (discount > subtotal) {
            discount = subtotal;
        }

        long finalAmount = Math.max(0, subtotal - discount);
        result.put("success", true);
        result.put("message", "Áp dụng mã giảm giá thành công: -" + String.format("%,d", discount) + " đ");
        result.put("discountAmount", discount);
        result.put("finalAmount", finalAmount);
        result.put("voucherCode", v.getCode());
        return result;
    }

    private void mapToEntity(Voucher voucher, VoucherRequest request) {
        voucher.setCode(request.getCode().trim().toUpperCase());
        voucher.setDescription(request.getDescription());
        voucher.setImageUrl(request.getImageUrl());
        voucher.setDiscountType(request.getDiscountType());
        voucher.setDiscountValue(request.getDiscountValue());
        voucher.setMinOrder(request.getMinOrder() != null ? request.getMinOrder() : BigDecimal.ZERO);
        voucher.setMaxDiscount(request.getMaxDiscount());
        voucher.setStartAt(request.getStartAt());
        voucher.setEndAt(request.getEndAt());
        voucher.setUsageLimit(request.getUsageLimit());
        voucher.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");
    }

    private VoucherResponse mapToResponse(Voucher v) {
        DecimalFormat df = new DecimalFormat("#,### đ");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        VoucherResponse r = new VoucherResponse();
        r.setVoucherId(v.getVoucherId());
        r.setCode(v.getCode());
        String computedTitle = v.getDescription() != null && !v.getDescription().isBlank() ? v.getDescription() : ("Ưu đãi " + v.getCode());
        r.setTitle(computedTitle);
        r.setDescription(v.getDescription());
        r.setImageUrl(v.getImageUrl());
        r.setDiscountType(v.getDiscountType());
        r.setDiscountValue(v.getDiscountValue());
        r.setMinOrder(v.getMinOrder());
        r.setMaxDiscount(v.getMaxDiscount());
        r.setStartAt(v.getStartAt());
        r.setEndAt(v.getEndAt());
        r.setUsageLimit(v.getUsageLimit());
        r.setStatus(v.getStatus());

        // Hiển thị định dạng trực quan cho Mobile (Ảnh 4)
        if ("PERCENT".equalsIgnoreCase(v.getDiscountType())) {
            r.setDiscountDisplay("Giảm " + v.getDiscountValue().intValue() + "%");
        } else {
            r.setDiscountDisplay("Giảm " + df.format(v.getDiscountValue()));
        }

        r.setMinOrderDisplay("Đơn tối thiểu: " + (v.getMinOrder() != null ? df.format(v.getMinOrder()) : "0đ"));
        r.setMaxDiscountDisplay(v.getMaxDiscount() != null ? ("Giảm tối đa: " + df.format(v.getMaxDiscount())) : null);
        r.setExpiryDisplay("HSD: " + (v.getEndAt() != null ? v.getEndAt().format(dtf) : "Vô thời hạn"));
        
        String startStr = v.getStartAt() != null ? v.getStartAt().format(dtf) : "";
        String endStr = v.getEndAt() != null ? v.getEndAt().format(dtf) : "";
        r.setPeriodDisplay((startStr.isEmpty() ? "" : startStr + " - ") + endStr);
        r.setRemainingUsage(v.getUsageLimit() != null ? v.getUsageLimit() : 999);

        return r;
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        String principal = authentication.getName();
        if (principal.contains("@")) {
            return userRepository.findByEmail(principal).orElse(null);
        }
        try {
            Long userId = Long.parseLong(principal);
            return userRepository.findById(userId).orElse(null);
        } catch (NumberFormatException e) {
            return userRepository.findByEmail(principal).orElse(null);
        }
    }
}