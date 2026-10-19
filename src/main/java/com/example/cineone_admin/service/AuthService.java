package com.example.cineone_admin.service;

import com.example.cineone_admin.entity.User;
import com.example.cineone_admin.enums.UserStatus;
import com.example.cineone_admin.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;

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
}