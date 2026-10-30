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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;

    @Autowired
    private MembershipTierRepository membershipTierRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

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

    public Map<String, Object> registerMobile(RegisterRequest request) {
        Map<String, Object> response = new HashMap<>();

        if (userRepository.existsByEmail(request.getEmail())) {
            response.put("success", false);
            response.put("status", 400);
            response.put("message", "Email đã được sử dụng.");
            response.put("data", null);
            return response;
        }

        MembershipTier defaultTier = membershipTierRepository.findById(1L)
                .orElse(membershipTierRepository.findAll().stream().findFirst().orElse(null));

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setEmailVerified(true);
        user.setPhoneVerified(true);
        user.setMembershipTier(defaultTier);

        userRepository.save(user);

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
        response.put("message", "Đăng ký tài khoản thành công.");
        response.put("data", authData);

        return response;
    }

    public Map<String, Object> loginMobile(LoginRequest request) {
        Map<String, Object> response = new HashMap<>();
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            response.put("success", false);
            response.put("status", 400);
            response.put("message", "Email hoặc mật khẩu không chính xác.");
            response.put("data", null);
            return response;
        }

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