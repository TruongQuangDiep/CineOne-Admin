package com.example.cineone_admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import java.io.Serializable;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
public class VoucherMembershipTierId implements Serializable {
    @Column(name = "voucher_id")
    private Long voucherId;

    @Column(name = "membership_tier_id")
    private Long membershipTierId;
}