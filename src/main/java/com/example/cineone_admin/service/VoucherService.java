package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.voucher.VoucherRequest;
import com.example.cineone_admin.dto.voucher.VoucherResponse;
import com.example.cineone_admin.entity.Voucher;
import com.example.cineone_admin.exception.BusinessException;
import com.example.cineone_admin.exception.ResourceNotFoundException;
import com.example.cineone_admin.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository voucherRepository;

    @Transactional(readOnly = true)
    public Page<VoucherResponse> getVouchers(String keyword, Pageable pageable) {
        return voucherRepository.searchVouchers(keyword, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<VoucherResponse> getMyVouchersMobile() {
        List<Voucher> vouchers = voucherRepository.findAll();
        List<VoucherResponse> responseList = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Voucher v : vouchers) {
            boolean isActive = "ACTIVE".equalsIgnoreCase(v.getStatus());
            boolean notExpired = v.getEndAt() == null || v.getEndAt().isAfter(now);

            if (isActive && notExpired) {
                responseList.add(mapToResponse(v));
            }
        }

        return responseList;
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

    @Transactional(readOnly = true)
    public Voucher getVoucherEntity(Long id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy voucher ID: " + id));
    }

    @Transactional(readOnly = true)
    public VoucherResponse getVoucherById(Long id) {
        return mapToResponse(getVoucherEntity(id));
    }

    @Transactional
    public void createVoucher(VoucherRequest request) {
        if (voucherRepository.findByCodeIgnoreCase(request.getCode()).isPresent()) {
            throw new BusinessException("Mã voucher '" + request.getCode() + "' đã tồn tại!");
        }
        Voucher v = new Voucher();
        mapRequestToEntity(request, v);
        voucherRepository.save(v);
    }

    @Transactional
    public void updateVoucher(Long id, VoucherRequest request) {
        Voucher v = getVoucherEntity(id);
        voucherRepository.findByCodeIgnoreCase(request.getCode())
                .filter(existing -> !existing.getVoucherId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessException("Mã voucher '" + request.getCode() + "' đã trùng với voucher khác!");
                });
        mapRequestToEntity(request, v);
        voucherRepository.save(v);
    }

    @Transactional
    public void toggleVoucherStatus(Long id) {
        Voucher v = getVoucherEntity(id);
        String current = v.getStatus() != null ? v.getStatus() : "ACTIVE";
        v.setStatus("ACTIVE".equalsIgnoreCase(current) ? "INACTIVE" : "ACTIVE");
        voucherRepository.save(v);
    }

    private void mapRequestToEntity(VoucherRequest req, Voucher v) {
        v.setCode(req.getCode().toUpperCase().trim());
        v.setDescription(req.getDescription());
        v.setImageUrl(req.getImageUrl());
        v.setDiscountType(req.getDiscountType());
        v.setDiscountValue(req.getDiscountValue());
        v.setMinOrder(req.getMinOrder() != null ? req.getMinOrder() : java.math.BigDecimal.ZERO);
        v.setMaxDiscount(req.getMaxDiscount());
        v.setUsageLimit(req.getUsageLimit());
        v.setStartAt(req.getStartAt());
        v.setEndAt(req.getEndAt());
        v.setStatus(req.getStatus() != null ? req.getStatus() : "ACTIVE");
    }

    public VoucherResponse mapToResponse(Voucher v) {
        VoucherResponse res = new VoucherResponse();
        res.setVoucherId(v.getVoucherId());
        res.setCode(v.getCode());
        res.setDescription(v.getDescription());
        res.setImageUrl(v.getImageUrl());
        res.setDiscountType(v.getDiscountType());
        res.setDiscountValue(v.getDiscountValue());
        res.setMinOrder(v.getMinOrder());
        res.setMaxDiscount(v.getMaxDiscount());
        res.setUsageLimit(v.getUsageLimit());
        res.setStartAt(v.getStartAt());
        res.setEndAt(v.getEndAt());
        res.setStatus(v.getStatus());
        return res;
    }
}