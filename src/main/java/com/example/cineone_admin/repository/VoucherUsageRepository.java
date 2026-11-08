package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.VoucherUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Long> {
    List<VoucherUsage> findByUser_UserId(Long userId);
    boolean existsByVoucher_VoucherIdAndUser_UserId(Long voucherId, Long userId);
}