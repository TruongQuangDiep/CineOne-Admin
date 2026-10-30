package com.example.cineone_admin.controller.api;

import com.example.cineone_admin.dto.user.UserProfileRequest;
import com.example.cineone_admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserApiController {

    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile() {
        Map<String, Object> body = userService.getProfileMobile();
        return ResponseEntity.status((int) body.getOrDefault("status", 200)).body(body);
    }

    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(@RequestBody UserProfileRequest request) {
        Map<String, Object> body = userService.updateProfileMobile(request);
        return ResponseEntity.status((int) body.getOrDefault("status", 200)).body(body);
    }
}