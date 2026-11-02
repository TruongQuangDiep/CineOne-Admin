package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.auth.AuthResponse;
import com.example.cineone_admin.dto.auth.LoginRequest;
import com.example.cineone_admin.dto.auth.RegisterRequest;
import com.example.cineone_admin.entity.MembershipTier;
import com.example.cineone_admin.entity.User;
import com.example.cineone_admin.enums.UserStatus;
import com.example.cineone_admin.repository.MembershipTierRepository;
import com.example.cineone_admin.repository.UserRepository;
import com.example.cineone_admin.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;
    private final MembershipTierRepository membershipTierRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final OtpService otpService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản với email: " + email));

        String role = (user.getRole() != null && !user.getRole().isBlank()) 
                ? user.getRole().trim().toUpperCase() 
                : "USER";
        String roleWithPrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;

        boolean isActive = UserStatus.ACTIVE.name().equalsIgnoreCase(user.getStatus());
        boolean isLocked = UserStatus.LOCKED.name().equalsIgnoreCase(user.getStatus());

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(Collections.singletonList(new SimpleGrantedAuthority(roleWithPrefix)))
                .accountLocked(isLocked)
                .disabled(!isActive && !isLocked)
                .build();
    }

    @Transactional
    public Map<String, Object> registerMobile(RegisterRequest request) {
        Map<String, Object> response = new HashMap<>();

        // 1. Kiểm tra trùng Email
        if (userRepository.existsByEmail(request.getEmail())) {
            response.put("success", false);
            response.put("status", 400);
            response.put("message", "Email đã được sử dụng.");
            response.put("data", null);
            return response;
        }

        // 2. Kiểm tra trùng Số điện thoại
        if (request.getPhone() != null && !request.getPhone().isBlank() 
                && userRepository.existsByPhone(request.getPhone().trim())) {
            response.put("success", false);
            response.put("status", 400);
            response.put("message", "Số điện thoại đã được sử dụng.");
            response.put("data", null);
            return response;
        }

        // 3. Lấy hạng thành viên mặc định
        MembershipTier defaultTier = membershipTierRepository.findById(1L)
                .orElse(membershipTierRepository.findAll().stream().findFirst().orElse(null));

        // 4. Tạo User mới với trạng thái emailVerified = false
        User user = new User();
        user.setEmail(request.getEmail().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setEmailVerified(false);
        user.setPhoneVerified(false);
        user.setMembershipTier(defaultTier);
        userRepository.save(user);

        // 5. Sinh và gửi mã OTP xác thực qua Email
        otpService.generateAndSendOtp(user, "EMAIL_VERIFY");

        // 6. Trả phản hồi yêu cầu xác thực OTP (không cấp Token ngay)
        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", user.getUserId());
        userData.put("email", user.getEmail());
        userData.put("emailVerified", false);

        response.put("success", true);
        response.put("status", 200);
        response.put("message", "Đăng ký tài khoản thành công! Vui lòng kiểm tra email để nhập mã OTP xác thực.");
        response.put("data", userData);
        return response;
    }

        public Map<String, Object> loginMobile(LoginRequest request) {
        Map<String, Object> response = new HashMap<>();

        String identifier = request.getEmail() != null ? request.getEmail().trim() : "";
        
        // TÌM THEO CẢ EMAIL HOẶC SỐ ĐIỆN THOẠI
        User user = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByPhone(identifier))
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            response.put("success", false);
            response.put("status", 400);
            response.put("message", "Email/Số điện thoại hoặc mật khẩu không chính xác.");
            response.put("data", null);
            return response;
        }

        // Kiểm tra xem tài khoản đã xác thực OTP chưa
        if (Boolean.FALSE.equals(user.getEmailVerified())) {
            response.put("success", false);
            response.put("status", 403);
            response.put("message", "Tài khoản chưa được kích hoạt qua email. Vui lòng xác thực mã OTP trước khi đăng nhập!");
            response.put("data", Map.of(
                    "userId", user.getUserId(),
                    "email", user.getEmail(),
                    "emailVerified", false
            ));
            return response;
        }

        // Kiểm tra trạng thái khóa tài khoản
        if (UserStatus.LOCKED.name().equalsIgnoreCase(user.getStatus())) {
            response.put("success", false);
            response.put("status", 403);
            response.put("message", "Tài khoản của bạn đã bị khóa.");
            response.put("data", null);
            return response;
        }

        // Cấp phát Token khi tài khoản đã xác thực hợp lệ
        String token = jwtTokenProvider.generateToken(user.getUserId(), user.getEmail(), user.getRole());
        AuthResponse authData = new AuthResponse();
        authData.setToken(token);
        authData.setTokenType("Bearer");
        authData.setUserId(user.getUserId());
        authData.setEmail(user.getEmail());
        authData.setFullName(user.getFullName());
        authData.setRole(user.getRole());

        response.put("success", true);
        response.put("status", 200);
        response.put("message", "Đăng nhập thành công.");
        response.put("data", authData);
        return response;
    }

    public ResponseEntity<Map<String, Object>> register(RegisterRequest request) {
        Map<String, Object> response = registerMobile(request);
        return ResponseEntity.status((int) response.getOrDefault("status", 200)).body(response);
    }

    public ResponseEntity<Map<String, Object>> login(LoginRequest request) {
        Map<String, Object> response = loginMobile(request);
        return ResponseEntity.status((int) response.getOrDefault("status", 200)).body(response);
    }
}