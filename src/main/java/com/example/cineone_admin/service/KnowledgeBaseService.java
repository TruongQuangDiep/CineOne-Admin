package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.knowledgebase.KnowledgeBaseRequest;
import com.example.cineone_admin.dto.support.FaqItemResponse;
import com.example.cineone_admin.entity.KnowledgeBaseEntry;
import com.example.cineone_admin.repository.KnowledgeBaseEntryRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KnowledgeBaseService {

    private final KnowledgeBaseEntryRepository repository;

    public static final List<String> DEFAULT_CATEGORIES = List.of("FAQ", "GUIDE", "POLICY", "SUPPORT");
    public static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "INACTIVE");

    public Page<KnowledgeBaseEntry> search(String keyword, String category, String status, int page, int size) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
                Predicate contentMatch = cb.like(cb.lower(root.get("content")), pattern);
                predicates.add(cb.or(titleMatch, contentMatch));
            }

            if (category != null && !category.isBlank()) {
                String normCat = normalizeCategoryForDb(category);
                predicates.add(cb.equal(root.get("category"), normCat));
            }

            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim().toUpperCase()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        }, PageRequest.of(Math.max(0, page), size, Sort.by(Sort.Direction.DESC, "knowledgeId")));
    }

    public KnowledgeBaseEntry getById(Long id) {
        return repository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết kiến thức với ID: " + id));
    }

    @Transactional
    public KnowledgeBaseEntry create(KnowledgeBaseRequest request) {
        validateRequest(request);

        if (repository.existsByTitleIgnoreCase(request.getTitle().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tiêu đề bài viết đã tồn tại.");
        }

        KnowledgeBaseEntry entry = new KnowledgeBaseEntry();
        entry.setTitle(request.getTitle().trim());
        entry.setCategory(normalizeCategoryForDb(request.getCategory()));
        entry.setContent(request.getContent().trim());
        entry.setStatus(request.getStatus().trim().toUpperCase());

        return repository.save(entry);
    }

    @Transactional
    public KnowledgeBaseEntry update(Long id, KnowledgeBaseRequest request) {
        validateRequest(request);
        KnowledgeBaseEntry entry = getById(id);

        if (repository.existsByTitleIgnoreCaseAndKnowledgeIdNot(request.getTitle().trim(), id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tiêu đề bài viết đã tồn tại ở bản ghi khác.");
        }

        entry.setTitle(request.getTitle().trim());
        entry.setCategory(normalizeCategoryForDb(request.getCategory()));
        entry.setContent(request.getContent().trim());
        entry.setStatus(request.getStatus().trim().toUpperCase());

        return repository.save(entry);
    }

    @Transactional
    public void toggleStatus(Long id) {
        KnowledgeBaseEntry entry = getById(id);
        entry.setStatus("ACTIVE".equalsIgnoreCase(entry.getStatus()) ? "INACTIVE" : "ACTIVE");
        repository.save(entry);
    }

    @Transactional
    public void delete(Long id) {
        KnowledgeBaseEntry entry = getById(id);
        repository.delete(entry);
    }

    private void validateRequest(KnowledgeBaseRequest request) {
        String normCat = request.getCategory() != null ? normalizeCategoryForDb(request.getCategory()) : "";
        if (!DEFAULT_CATEGORIES.contains(normCat)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh mục không hợp lệ.");
        }
        if (request.getStatus() == null || !VALID_STATUSES.contains(request.getStatus().trim().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ.");
        }
    }

    private static String normalizeCategoryForDb(String category) {
        if (category == null) return "";
        String c = category.trim().toUpperCase(Locale.ROOT);
        if ("CHÍNH SÁCH".equals(c) || "CHINH SACH".equals(fold(c))) return "POLICY";
        if ("HƯỚNG DẪN".equals(c) || "HUONG DAN".equals(fold(c))) return "GUIDE";
        if ("HỖ TRỢ".equals(c) || "HO TRO".equals(fold(c))) return "SUPPORT";
        return c;
    }

    public List<FaqItemResponse> getActiveFaqs(String category, String keyword) {
        List<KnowledgeBaseEntry> activeEntries = repository.findByStatusOrderByKnowledgeIdAsc("ACTIVE");
        String normCat = category == null ? "" : normalizeCategoryLabel(category);
        String normKeyword = keyword == null ? "" : fold(keyword);

        return activeEntries.stream()
                .filter(entry -> {
                    if (normCat.isEmpty() || "ALL".equalsIgnoreCase(normCat) || "TẤT CẢ".equalsIgnoreCase(normCat)) {
                        return true;
                    }
                    String entryCat = normalizeCategoryLabel(entry.getCategory());
                    return entryCat.equalsIgnoreCase(normCat) || fold(entryCat).equals(fold(normCat));
                })
                .filter(entry -> {
                    if (normKeyword.isEmpty()) {
                        return true;
                    }
                    String combined = fold((entry.getTitle() != null ? entry.getTitle() : "") + " " + (entry.getContent() != null ? entry.getContent() : ""));
                    return combined.contains(normKeyword);
                })
                .map(entry -> FaqItemResponse.builder()
                        .id(entry.getKnowledgeId())
                        .title(entry.getTitle())
                        .content(entry.getContent())
                        .category(normalizeCategoryLabel(entry.getCategory()))
                        .build())
                .toList();
    }

    private static String normalizeCategoryLabel(String category) {
        if (category == null || category.isBlank()) {
            return "FAQ";
        }
        String c = category.trim().toUpperCase(Locale.ROOT);
        if ("POLICY".equals(c) || "CHÍNH SÁCH".equals(c) || "CHINH SACH".equals(fold(c))) {
            return "CHÍNH SÁCH";
        }
        if ("GUIDE".equals(c) || "HƯỚNG DẪN".equals(c) || "HUONG DAN".equals(fold(c))) {
            return "HƯỚNG DẪN";
        }
        if ("SUPPORT".equals(c) || "HỖ TRỢ".equals(c) || "HO TRO".equals(fold(c))) {
            return "HỖ TRỢ";
        }
        return c;
    }

    private static String fold(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
