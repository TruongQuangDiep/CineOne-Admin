package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.user.UserProfileRequest;
import com.example.cineone_admin.dto.user.UserRequest;
import com.example.cineone_admin.dto.user.UserResponse;
import com.example.cineone_admin.entity.MembershipTier;
import com.example.cineone_admin.entity.User;
import com.example.cineone_admin.repository.MembershipTierRepository;
import com.example.cineone_admin.repository.UserRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository users;
    private final MembershipTierRepository tiers;

    public Page<UserResponse> search(String q, Long tierId, String status, int page) {
        String search = q == null ? "" : q.strip();
        return users.findAll((root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("membershipTier", JoinType.LEFT);
            }
            List<Predicate> filters = new ArrayList<>();
            if (tierId != null) {
                filters.add(cb.equal(root.get("membershipTier").get("membershipTierId"), tierId));
            }
            if (status != null && !status.isBlank()) {
                filters.add(cb.equal(root.get("status"), status));
            }
            if (!search.isEmpty()) {
                String term = like(search);
                filters.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), term, '\\'),
                        cb.like(cb.lower(root.get("email")), term, '\\'),
                        cb.like(cb.lower(root.get("phone")), term, '\\')
                ));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        }, PageRequest.of(Math.max(0, page), 10, Sort.by(Sort.Direction.DESC, "userId")))
                .map(this::toResponse);
    }

    public UserResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    public User getEntity(Long id) {
        return users.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
    }

    @Transactional
    public User update(Long id, @Valid UserRequest request) {
        User user = getEntity(id);
        MembershipTier tier = tiers.findById(request.getMembershipTierId()).orElseThrow(() ->
                new IllegalArgumentException("Hạng thành viên không tồn tại"));

        user.setMembershipTier(tier);
        return users.save(user);
    }

    @Transactional
    public void setStatus(Long id, String status) {
        getEntity(id).setStatus(status);
    }

    public Map<String, Object> getProfileMobile() {
        Map<String, Object> response = new HashMap<>();
        User user = getAuthenticatedUser();

        if (user == null) {
            response.put("success", false);
            response.put("status", 404);
            response.put("message", "Không tìm thấy người dùng hoặc Token không hợp lệ.");
            response.put("data", null);
            return response;
        }

        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", user.getUserId());
        userData.put("email", user.getEmail());
        userData.put("fullName", user.getFullName());
        userData.put("phone", user.getPhone());
        userData.put("homeAddress", user.getHomeAddress());
        userData.put("membershipTier", user.getMembershipTier() != null ? user.getMembershipTier().getName() : "NORMAL");

        response.put("success", true);
        response.put("status", 200);
        response.put("message", "Lấy thông tin cá nhân thành công.");
        response.put("data", userData);

        return response;
    }

    @Transactional
    public Map<String, Object> updateProfileMobile(UserProfileRequest request) {
        Map<String, Object> response = new HashMap<>();
        User user = getAuthenticatedUser();

        if (user == null) {
            response.put("success", false);
            response.put("status", 404);
            response.put("message", "Không tìm thấy người dùng hoặc Token không hợp lệ.");
            response.put("data", null);
            return response;
        }

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getHomeAddress() != null) {
            user.setHomeAddress(request.getHomeAddress());
        }

        users.save(user);

        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", user.getUserId());
        userData.put("email", user.getEmail());
        userData.put("fullName", user.getFullName());
        userData.put("phone", user.getPhone());
        userData.put("homeAddress", user.getHomeAddress());
        userData.put("membershipTier", user.getMembershipTier() != null ? user.getMembershipTier().getName() : "NORMAL");

        response.put("success", true);
        response.put("status", 200);
        response.put("message", "Cập nhật thông tin thành công.");
        response.put("data", userData);

        return response;
    }

    public ResponseEntity<Map<String, Object>> getProfile() {
        Map<String, Object> response = getProfileMobile();
        return ResponseEntity.status((int) response.getOrDefault("status", 200)).body(response);
    }

    @Transactional
    public ResponseEntity<Map<String, Object>> updateProfile(UserProfileRequest request) {
        Map<String, Object> response = updateProfileMobile(request);
        return ResponseEntity.status((int) response.getOrDefault("status", 200)).body(response);
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String principal = authentication.getName();
        if (principal.contains("@")) {
            return users.findByEmail(principal).orElse(null);
        }

        try {
            Long userId = Long.parseLong(principal);
            return users.findById(userId).orElse(null);
        } catch (NumberFormatException e) {
            return users.findByEmail(principal).orElse(null);
        }
    }

    private UserResponse toResponse(User u) {
        var tier = u.getMembershipTier();
        return new UserResponse(
                u.getUserId(),
                u.getEmail(),
                u.getPhone(),
                u.getFullName(),
                u.getDateOfBirth(),
                u.getRole(),
                tier != null ? tier.getMembershipTierId() : null,
                tier != null ? tier.getName() : "Mặc định",
                tier != null ? tier.getMinSpending() : null,
                u.getHomeAddress(),
                u.getEmailVerified(),
                u.getPhoneVerified(),
                u.getStatus(),
                u.getCreatedAt()
        );
    }

    private static String like(String value) {
        String text = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}