package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.voucher.VoucherRequest;
import com.example.cineone_admin.dto.voucher.VoucherResponse;
import com.example.cineone_admin.entity.Voucher;
import com.example.cineone_admin.exception.BusinessException;
import com.example.cineone_admin.exception.ResourceNotFoundException;
import com.example.cineone_admin.repository.VoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTests {

    @Mock
    private VoucherRepository voucherRepository;

    @InjectMocks
    private VoucherService voucherService;

    private Voucher mockVoucher;

    @BeforeEach
    void setUp() {
        mockVoucher = new Voucher();
        mockVoucher.setVoucherId(1L);
        mockVoucher.setCode("CINE50");
        mockVoucher.setDescription("Giảm giá 50k");
        mockVoucher.setDiscountType("FIXED");
        mockVoucher.setDiscountValue(BigDecimal.valueOf(50000));
        mockVoucher.setMinOrder(BigDecimal.valueOf(100000));
        mockVoucher.setUsageLimit(50);
        mockVoucher.setStatus("ACTIVE");
        mockVoucher.setStartAt(LocalDateTime.now());
        mockVoucher.setEndAt(LocalDateTime.now().plusMonths(1));
    }

    @Test
    void getVouchers_ReturnsMappedPage() {
        var pageable = PageRequest.of(0, 10);
        when(voucherRepository.searchVouchers(eq("CINE"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(mockVoucher)));

        var result = voucherService.getVouchers("CINE", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        VoucherResponse res = result.getContent().get(0);
        assertEquals("CINE50", res.getCode());
        assertEquals("ACTIVE", res.getStatus());
        assertEquals(BigDecimal.valueOf(50000), res.getDiscountValue());
    }

    @Test
    void createVoucher_ThrowsExceptionWhenCodeExists() {
        VoucherRequest request = new VoucherRequest();
        request.setCode("CINE50");

        when(voucherRepository.findByCodeIgnoreCase("CINE50")).thenReturn(Optional.of(mockVoucher));

        BusinessException ex = assertThrows(BusinessException.class, () -> voucherService.createVoucher(request));
        assertTrue(ex.getMessage().contains("đã tồn tại"));
        verify(voucherRepository, never()).save(any());
    }

    @Test
    void createVoucher_SavesSuccessfullyWhenCodeIsNew() {
        VoucherRequest request = new VoucherRequest();
        request.setCode("NEWCODE");
        request.setDiscountType("PERCENT");
        request.setDiscountValue(BigDecimal.valueOf(20));
        request.setStartAt(LocalDateTime.now());
        request.setEndAt(LocalDateTime.now().plusDays(7));

        when(voucherRepository.findByCodeIgnoreCase("NEWCODE")).thenReturn(Optional.empty());

        voucherService.createVoucher(request);

        verify(voucherRepository, times(1)).save(any(Voucher.class));
    }

    @Test
    void toggleVoucherStatus_SwitchesBetweenActiveAndInactive() {
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(mockVoucher));

        // Lần 1: Đang ACTIVE -> chuyển sang INACTIVE
        voucherService.toggleVoucherStatus(1L);
        assertEquals("INACTIVE", mockVoucher.getStatus());

        // Lần 2: Đang INACTIVE -> chuyển lại ACTIVE
        voucherService.toggleVoucherStatus(1L);
        assertEquals("ACTIVE", mockVoucher.getStatus());

        verify(voucherRepository, times(2)).save(mockVoucher);
    }
}