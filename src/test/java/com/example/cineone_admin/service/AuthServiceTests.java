package com.example.cineone_admin.service;

import com.example.cineone_admin.entity.User;
import com.example.cineone_admin.enums.UserStatus;
import com.example.cineone_admin.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Ném UsernameNotFoundException khi không tìm thấy email")
    void shouldThrowExceptionWhenUserNotFound() {
        when(userRepository.findByEmail("unknown@cineone.vn")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> authService.loadUserByUsername("unknown@cineone.vn"));
    }

    @Test
    @DisplayName("Tải thông tin UserDetails thành công với tài khoản ACTIVE và quyền ADMIN")
    void shouldLoadActiveAdminUserSuccessfully() {
        User user = new User();
        user.setEmail("admin@cineone.vn");
        user.setPasswordHash("hashed_password_123");
        user.setRole("ADMIN");
        user.setStatus(UserStatus.ACTIVE.name());

        when(userRepository.findByEmail("admin@cineone.vn")).thenReturn(Optional.of(user));

        var userDetails = authService.loadUserByUsername("admin@cineone.vn");

        assertEquals("admin@cineone.vn", userDetails.getUsername());
        assertEquals("hashed_password_123", userDetails.getPassword());
        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.isAccountNonLocked());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("Tài khoản LOCKED thì accountNonLocked phải bằng false")
    void shouldLockAccountWhenStatusIsLocked() {
        User user = new User();
        user.setEmail("locked@cineone.vn");
        user.setPasswordHash("hashed_pwd");
        user.setRole("ADMIN");
        user.setStatus(UserStatus.LOCKED.name());

        when(userRepository.findByEmail("locked@cineone.vn")).thenReturn(Optional.of(user));

        var userDetails = authService.loadUserByUsername("locked@cineone.vn");

        assertFalse(userDetails.isAccountNonLocked());
    }

    @Test
    @DisplayName("Tài khoản INACTIVE thì enabled phải bằng false")
    void shouldDisableAccountWhenStatusIsInactive() {
        User user = new User();
        user.setEmail("inactive@cineone.vn");
        user.setPasswordHash("hashed_pwd");
        user.setRole("ADMIN");
        user.setStatus(UserStatus.INACTIVE.name());

        when(userRepository.findByEmail("inactive@cineone.vn")).thenReturn(Optional.of(user));

        var userDetails = authService.loadUserByUsername("inactive@cineone.vn");

        assertFalse(userDetails.isEnabled());
    }

    @Test
    @DisplayName("Role là null hoặc rỗng thì mặc định gán ROLE_USER, không cấp quyền ADMIN")
    void shouldDefaultToUserRoleWhenRoleIsMissing() {
        User user = new User();
        user.setEmail("norole@cineone.vn");
        user.setPasswordHash("hashed_pwd");
        user.setRole(null); 
        user.setStatus(UserStatus.ACTIVE.name());

        when(userRepository.findByEmail("norole@cineone.vn")).thenReturn(Optional.of(user));

        var userDetails = authService.loadUserByUsername("norole@cineone.vn");

        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        assertFalse(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("Trạng thái lạ, gõ sai chính tả hoặc null phải bị vô hiệu hóa (disabled)")
    void shouldDisableAccountWhenStatusIsInvalidOrTypo() {
        User userWithTypo = new User();
        userWithTypo.setEmail("typo@cineone.vn");
        userWithTypo.setPasswordHash("hashed_pwd");
        userWithTypo.setRole("ADMIN");
        userWithTypo.setStatus("ACTVE"); // Sai chính tả

        when(userRepository.findByEmail("typo@cineone.vn")).thenReturn(Optional.of(userWithTypo));

        var userDetails = authService.loadUserByUsername("typo@cineone.vn");

        assertFalse(userDetails.isEnabled());
    }
}