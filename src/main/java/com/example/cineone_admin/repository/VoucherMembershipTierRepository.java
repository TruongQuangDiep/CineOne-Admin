package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.VoucherMembershipTier;
import com.example.cineone_admin.entity.VoucherMembershipTierId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VoucherMembershipTierRepository extends JpaRepository<VoucherMembershipTier, VoucherMembershipTierId> {
    List<VoucherMembershipTier> findByVoucher_VoucherId(Long voucherId);
    void deleteByVoucher_VoucherId(Long voucherId);
}