package com.example.cineone_admin.dto.user;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record UserResponse(
        Long userId,
        String email,
        String phone,
        String fullName,
        LocalDate dateOfBirth,
        String role,
        Long membershipTierId,
        String membershipTierName,
        BigDecimal minSpending,
        String homeAddress,
        Boolean emailVerified,
        Boolean phoneVerified,
        String status,
        LocalDateTime createdAt
) {}