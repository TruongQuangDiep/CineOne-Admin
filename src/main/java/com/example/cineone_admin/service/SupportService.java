package com.example.cineone_admin.service;

import com.example.cineone_admin.entity.SupportRequest;
import com.example.cineone_admin.exception.ResourceNotFoundException;
import com.example.cineone_admin.repository.SupportRequestRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportService {

    private final SupportRequestRepository repository;

    public static final List<String> STATUS_OPTIONS = List.of("PENDING", "IN_PROGRESS", "RESOLVED", "REJECTED");
    public static final List<String> TYPE_OPTIONS = List.of("HỖ TRỢ", "KHIẾU NẠI");
    public static final List<String> CATEGORY_OPTIONS = List.of("THANH TOÁN", "VÉ", "ĐÁNH GIÁ", "TÀI KHOẢN", "KHÁC");

    public Page<SupportRequest> search(String keyword, String status, String type, String category, int page, int size) {
        return repository.findAll((root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("user", JoinType.LEFT);
            }
            var predicates = new ArrayList<Predicate>();

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                Predicate contentMatch = cb.like(cb.lower(root.get("content")), pattern);
                var userJoin = root.join("user", JoinType.LEFT);
                Predicate userEmailMatch = cb.like(cb.lower(userJoin.get("email")), pattern);
                predicates.add(cb.or(contentMatch, userEmailMatch));
            }

            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim().toUpperCase()));
            }

            if (type != null && !type.isBlank()) {
                predicates.add(cb.equal(root.get("type"), type.trim()));
            }

            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category.trim()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        }, PageRequest.of(Math.max(0, page), size, Sort.by(Sort.Direction.DESC, "requestId")));
    }

    public SupportRequest getById(Long id) {
        return repository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu hỗ trợ với ID: " + id));
    }

    @Transactional
    public SupportRequest respond(Long id, String responseText, String newStatus) {
        SupportRequest request = getById(id);

        if (responseText == null || responseText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung phản hồi không được để trống.");
        }
        if (newStatus == null || !STATUS_OPTIONS.contains(newStatus.trim().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái cập nhật không hợp lệ.");
        }

        request.setResponse(responseText.trim());
        request.setStatus(newStatus.trim().toUpperCase());
        request.setRespondedAt(LocalDateTime.now());
        request.setEmailStatus("PENDING");

        return repository.save(request);
    }

    @Transactional
    public void updateStatus(Long id, String newStatus) {
        SupportRequest request = getById(id);
        if (newStatus == null || !STATUS_OPTIONS.contains(newStatus.trim().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ.");
        }
        request.setStatus(newStatus.trim().toUpperCase());
        repository.save(request);
    }
}