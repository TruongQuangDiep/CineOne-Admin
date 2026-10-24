package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.membership.MembershipTierRequest;
import com.example.cineone_admin.entity.MembershipTier;
import com.example.cineone_admin.repository.MembershipTierRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipService {
    private final MembershipTierRepository repository;

    public List<MembershipTier> all() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "minSpending"));
    }

    public MembershipTier get(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hạng thành viên"));
    }

    @Transactional
    public MembershipTier save(Long id, @Valid MembershipTierRequest request) {
        MembershipTier tier = id == null ? new MembershipTier() : get(id);
        repository.findByName(request.getName().strip()).ifPresent(existing -> {
            if (!Objects.equals(existing.getMembershipTierId(), id)) {
                throw new IllegalArgumentException("Tên hạng thành viên đã tồn tại");
            }
        });
        tier.setName(request.getName().strip());
        tier.setMinSpending(request.getMinSpending());
        tier.setDescription(request.getDescription());
        tier.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");
        return repository.save(tier);
    }

    @Transactional
    public void setStatus(Long id, boolean enabled) {
        get(id).setStatus(enabled ? "ACTIVE" : "INACTIVE");
    }
}