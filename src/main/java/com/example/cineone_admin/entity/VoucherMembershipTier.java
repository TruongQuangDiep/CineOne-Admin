package com.example.cineone_admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "VoucherMembershipTiers")
@Getter
@Setter
public class VoucherMembershipTier {
    @EmbeddedId
    private VoucherMembershipTierId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("voucherId")
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("membershipTierId")
    @JoinColumn(name = "membership_tier_id", nullable = false)
    private MembershipTier membershipTier;
}