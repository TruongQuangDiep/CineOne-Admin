package com.example.cineone_admin.dto.auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthResponse {
    private String token;
    private String tokenType = "Bearer";
    private Long userId;
    private String email;
    private String fullName;
    private String role;
}