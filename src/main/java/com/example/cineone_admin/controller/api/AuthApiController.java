package com.example.cineone_admin.controller.api;

import com.example.cineone_admin.dto.auth.LoginRequest;
import com.example.cineone_admin.dto.auth.RegisterRequest;
import com.example.cineone_admin.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        Map<String, Object> body = authService.registerMobile(request);
        return ResponseEntity.status((int) body.getOrDefault("status", 200)).body(body);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        Map<String, Object> body = authService.loginMobile(request);
        return ResponseEntity.status((int) body.getOrDefault("status", 200)).body(body);
    }
}