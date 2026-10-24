package com.example.cineone_admin.service;

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