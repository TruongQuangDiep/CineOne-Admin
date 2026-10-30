package com.example.cineone_admin.dto.user;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserProfileResponse {
    private Long userId;
    private String email;
    private String fullName;
    private String phone;
    private String homeAddress;
    private String membershipTier;
    private String role;
}