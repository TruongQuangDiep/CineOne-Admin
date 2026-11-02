package com.example.cineone_admin.controller.api;

import com.example.cineone_admin.dto.auth.LoginRequest;
import com.example.cineone_admin.dto.auth.RegisterRequest;
import com.example.cineone_admin.entity.User;
import com.example.cineone_admin.repository.UserRepository;
import com.example.cineone_admin.service.AuthService;
import com.example.cineone_admin.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final AuthService authService;
    private final OtpService otpService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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

    // Gửi OTP đặt lại mật khẩu (type = RESET_PASS)
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "message", "Email không được để trống"
            ));
        }

        Optional<User> userOptional = userRepository.findByEmail(email.trim());
        if (userOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Email chưa được đăng ký trong hệ thống"
            ));
        }

        otpService.generateAndSendOtp(userOptional.get(), "RESET_PASS");

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "message", "Mã OTP đặt lại mật khẩu đã được gửi đến email"
        ));
    }

    // Xác thực OTP (nhận type: EMAIL_VERIFY hoặc RESET_PASS)
    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otp = request.get("otp");
        String type = request.getOrDefault("type", "EMAIL_VERIFY");

        if (email == null || email.isBlank() || otp == null || otp.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "message", "Vui lòng nhập đầy đủ email và mã OTP"
            ));
        }

        boolean isValid = otpService.verifyOtp(email.trim(), otp.trim(), type);
        if (!isValid) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", 400,
                    "message", "Mã OTP không chính xác hoặc đã hết hạn"
            ));
        }

        // Nếu là xác thực email khi đăng ký -> Cập nhật email_verified = true
        if ("EMAIL_VERIFY".equalsIgnoreCase(type)) {
            userRepository.findByEmail(email.trim()).ifPresent(user -> {
                user.setEmailVerified(true);
                userRepository.save(user);
            });
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "message", "Xác thực OTP thành công"
        ));
    }

    // Đặt lại mật khẩu mới
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String newPassword = request.get("newPassword");

        if (email == null || email.isBlank() || newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "message", "Vui lòng cung cấp email và mật khẩu mới"
            ));
        }

        Optional<User> userOptional = userRepository.findByEmail(email.trim());
        if (userOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Không tìm thấy thông tin tài khoản"
            ));
        }

        User user = userOptional.get();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "message", "Đặt lại mật khẩu thành công. Bạn có thể đăng nhập lại"
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        // Nếu có triển khai token blacklist hoặc FCM token, hủy tại đây
        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", "Đăng xuất thành công."
        ));
    }
}