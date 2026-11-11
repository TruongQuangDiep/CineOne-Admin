package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.support.CreateSupportRequest;
import com.example.cineone_admin.dto.support.SupportResponse;
import com.example.cineone_admin.entity.Booking;
import com.example.cineone_admin.entity.SupportRequest;
import com.example.cineone_admin.entity.Ticket;
import com.example.cineone_admin.entity.User;
import com.example.cineone_admin.repository.BookingRepository;
import com.example.cineone_admin.repository.SupportRequestRepository;
import com.example.cineone_admin.repository.TicketRepository;
import com.example.cineone_admin.repository.UserRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportService {

    private final SupportRequestRepository repository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static final List<String> STATUS_OPTIONS = List.of("PENDING", "PROCESSING", "RESOLVED", "CLOSED");
    public static final List<String> TYPE_OPTIONS = List.of("SUPPORT", "COMPLAINT");
    public static final List<String> CATEGORY_OPTIONS = List.of("ACCOUNT", "BOOKING", "PAYMENT", "TICKET", "REVIEW", "OTHER");

    public Page<SupportRequest> search(String keyword, String status, String type, String category, int page, int size) {
        return repository.findAll((root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("user", JoinType.LEFT);
            }
            var predicates = new ArrayList<Predicate>();

            if (keyword != null && !keyword.isBlank()) {
                String trimmed = keyword.trim().toLowerCase();
                String pattern = "%" + trimmed + "%";
                Predicate contentMatch = cb.like(cb.lower(root.get("content")), pattern);
                var userJoin = root.join("user", JoinType.LEFT);
                Predicate userEmailMatch = cb.like(cb.lower(userJoin.get("email")), pattern);
                Predicate userNameMatch = cb.like(cb.lower(userJoin.get("fullName")), pattern);
                String digits = trimmed.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    try {
                        long reqId = Long.parseLong(digits);
                        Predicate idMatch = cb.equal(root.get("requestId"), reqId);
                        predicates.add(cb.or(contentMatch, userEmailMatch, userNameMatch, idMatch));
                    } catch (NumberFormatException ignored) {
                        predicates.add(cb.or(contentMatch, userEmailMatch, userNameMatch));
                    }
                } else {
                    predicates.add(cb.or(contentMatch, userEmailMatch, userNameMatch));
                }
            }

            if (status != null && !status.isBlank()) {
                String dbStatus = toDbStatusOrNull(status);
                predicates.add(cb.equal(root.get("status"), dbStatus != null ? dbStatus : status.trim().toUpperCase()));
            }

            if (type != null && !type.isBlank()) {
                predicates.add(cb.equal(root.get("type"), toDbType(type)));
            }

            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), toDbCategory(category)));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        }, PageRequest.of(Math.max(0, page), size, Sort.by(Sort.Direction.DESC, "requestId")));
    }

    public SupportRequest getById(Long id) {
        SupportRequest sr = repository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu hỗ trợ với ID: " + id));
        if (sr.getUser() != null) {
            sr.getUser().getFullName();
        }
        if (sr.getBooking() != null) {
            sr.getBooking().getBookingCode();
        }
        return sr;
    }

    @Transactional
    public SupportRequest respond(Long id, String responseText, String newStatus) {
        SupportRequest request = getById(id);

        if (responseText == null || responseText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung phản hồi không được để trống.");
        }
        String dbStatus = toDbStatusOrNull(newStatus);
        if (dbStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái cập nhật không hợp lệ.");
        }

        request.setResponse(responseText.trim());
        request.setStatus(dbStatus);
        request.setRespondedAt(LocalDateTime.now());
        request.setEmailStatus("PENDING");

        return repository.save(request);
    }

    @Transactional
    public void updateStatus(Long id, String newStatus) {
        SupportRequest request = getById(id);
        String dbStatus = toDbStatusOrNull(newStatus);
        if (dbStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ.");
        }
        request.setStatus(dbStatus);
        repository.save(request);
    }

    @Transactional
    public SupportResponse createMobileRequest(CreateSupportRequest req, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để gửi yêu cầu hỗ trợ / khiếu nại.");
        }

        if (req == null || req.getContent() == null || req.getContent().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung yêu cầu không được để trống.");
        }

        Booking booking = resolveBooking(req.getBookingId(), req.getBookingCode());

        SupportRequest entity = new SupportRequest();
        entity.setUser(user);
        entity.setBooking(booking);
        entity.setType(toDbType(req.getType()));
        entity.setCategory(toDbCategory(req.getCategory()));
        entity.setContent(req.getContent().trim());
        entity.setStatus("PENDING");
        entity.setEmailStatus("PENDING");

        SupportRequest saved = repository.saveAndFlush(entity);
        return toSupportResponse(saved);
    }

    @Transactional
    public SupportResponse updateMobileRequest(Long requestId, CreateSupportRequest req, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để chỉnh sửa yêu cầu.");
        }

        SupportRequest entity = getById(requestId);
        if (entity.getUser() == null || !entity.getUser().getUserId().equals(user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền chỉnh sửa yêu cầu này.");
        }

        String currentStatus = entity.getStatus() != null ? entity.getStatus().trim().toUpperCase() : "PENDING";
        if (!"PENDING".equals(currentStatus) || (entity.getResponse() != null && !entity.getResponse().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể chỉnh sửa yêu cầu khi đang ở trạng thái Chờ xử lý.");
        }

        if (req == null || req.getContent() == null || req.getContent().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung yêu cầu không được để trống.");
        }

        Booking booking = resolveBooking(req.getBookingId(), req.getBookingCode());
        entity.setBooking(booking);
        if (req.getType() != null && !req.getType().isBlank()) {
            entity.setType(toDbType(req.getType()));
        }
        if (req.getCategory() != null && !req.getCategory().isBlank()) {
            entity.setCategory(toDbCategory(req.getCategory()));
        }
        entity.setContent(req.getContent().trim());

        SupportRequest saved = repository.saveAndFlush(entity);
        return toSupportResponse(saved);
    }

    @Transactional
    public void deleteMobileRequest(Long requestId, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để xóa yêu cầu.");
        }

        SupportRequest entity = getById(requestId);
        if (entity.getUser() == null || !entity.getUser().getUserId().equals(user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xóa yêu cầu này.");
        }

        String currentStatus = entity.getStatus() != null ? entity.getStatus().trim().toUpperCase() : "PENDING";
        if (!"PENDING".equals(currentStatus) || (entity.getResponse() != null && !entity.getResponse().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể xóa yêu cầu khi đang ở trạng thái Chờ xử lý.");
        }

        repository.delete(entity);
        repository.flush();
    }

    private Booking resolveBooking(Long bookingId, String bookingCode) {
        Booking booking = null;
        if (bookingId != null && bookingId > 0) {
            booking = bookingRepository.findById(bookingId).orElse(null);
        } else if (bookingCode != null && !bookingCode.isBlank()) {
            String code = bookingCode.trim();
            booking = bookingRepository.findByBookingCodeIgnoreCase(code).orElse(null);
            if (booking == null) {
                booking = ticketRepository.findFirstByTicketCodeIgnoreCase(code)
                        .map(Ticket::getBooking)
                        .orElse(null);
            }
            if (booking == null) {
                try {
                    long parsedId = Long.parseLong(code.replaceAll("[^0-9]", ""));
                    booking = bookingRepository.findById(parsedId).orElse(null);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return booking;
    }

    public List<SupportResponse> getMyRequests(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để xem lịch sử yêu cầu hỗ trợ.");
        }

        return repository.findByUser_UserIdOrderByCreatedAtDescRequestIdDesc(user.getUserId())
                .stream()
                .map(this::toSupportResponse)
                .toList();
    }

    public SupportResponse getMyRequestDetail(Long requestId, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để xem chi tiết yêu cầu.");
        }

        SupportRequest entity = getById(requestId);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getRole());
        if (!isAdmin && (entity.getUser() == null || !entity.getUser().getUserId().equals(user.getUserId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem yêu cầu hỗ trợ này.");
        }

        return toSupportResponse(entity);
    }

    private SupportResponse toSupportResponse(SupportRequest sr) {
        Booking b = sr.getBooking();
        LocalDateTime created = sr.getCreatedAt() != null ? sr.getCreatedAt() : LocalDateTime.now();
        String createdStr = created.format(DATE_FORMATTER);
        String respondedStr = sr.getRespondedAt() != null ? sr.getRespondedAt().format(DATE_FORMATTER) : null;

        return SupportResponse.builder()
                .requestId(sr.getRequestId())
                .type(toDisplayType(sr.getType()))
                .category(toDisplayCategory(sr.getCategory()))
                .content(sr.getContent())
                .status(toApiStatus(sr.getStatus()))
                .response(sr.getResponse())
                .bookingId(b != null ? b.getBookingId() : null)
                .bookingCode(b != null ? b.getBookingCode() : null)
                .respondedAt(respondedStr)
                .createdAt(createdStr)
                .build();
    }

    private String toDbStatusOrNull(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String s = status.trim().toUpperCase();
        if ("IN_PROGRESS".equals(s)) {
            return "PROCESSING";
        }
        if ("REJECTED".equals(s)) {
            return "CLOSED";
        }
        if (STATUS_OPTIONS.contains(s)) {
            return s;
        }
        return null;
    }

    private String toApiStatus(String status) {
        if (status == null || status.isBlank()) {
            return "PENDING";
        }
        String s = status.trim().toUpperCase();
        if ("PROCESSING".equals(s)) {
            return "IN_PROGRESS";
        }
        if ("CLOSED".equals(s)) {
            return "REJECTED";
        }
        return s;
    }

    private String toDbType(String type) {
        if (type == null || type.isBlank()) {
            return "SUPPORT";
        }
        String t = type.trim().toUpperCase();
        if (t.contains("KHIẾU") || t.contains("KHIEU") || t.equals("COMPLAINT")) {
            return "COMPLAINT";
        }
        return "SUPPORT";
    }

    private String toDisplayType(String type) {
        if ("COMPLAINT".equalsIgnoreCase(type) || (type != null && type.toUpperCase().contains("KHIẾU"))) {
            return "KHIẾU NẠI";
        }
        return "HỖ TRỢ";
    }

    private String toDbCategory(String category) {
        if (category == null || category.isBlank()) {
            return "OTHER";
        }
        String c = category.trim().toUpperCase();
        if (CATEGORY_OPTIONS.contains(c)) {
            return c;
        }
        if (c.contains("THANH")) return "PAYMENT";
        if (c.contains("ĐẶT") || c.contains("DAT")) return "BOOKING";
        if (c.contains("VÉ") || c.equals("VE")) return "TICKET";
        if (c.contains("ĐÁNH") || c.contains("DANH")) return "REVIEW";
        if (c.contains("TÀI") || c.contains("TAI")) return "ACCOUNT";
        return "OTHER";
    }

    private String toDisplayCategory(String category) {
        if (category == null || category.isBlank()) {
            return "KHÁC";
        }
        String c = category.trim().toUpperCase();
        return switch (c) {
            case "PAYMENT", "THANH TOÁN" -> "THANH TOÁN";
            case "TICKET", "BOOKING", "VÉ", "ĐẶT VÉ" -> "VÉ";
            case "REVIEW", "ĐÁNH GIÁ" -> "ĐÁNH GIÁ";
            case "ACCOUNT", "TÀI KHOẢN" -> "TÀI KHOẢN";
            default -> "KHÁC";
        };
    }

    private User getAuthenticatedUser(Authentication authParam) {
        Authentication authentication = authParam != null ? authParam : SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        String principal = authentication.getName();
        if (principal == null || principal.isBlank()) {
            return null;
        }
        if (principal.contains("@")) {
            return userRepository.findByEmail(principal).orElse(null);
        }
        try {
            long userId = Long.parseLong(principal);
            return userRepository.findById(userId).orElse(null);
        } catch (NumberFormatException e) {
            return userRepository.findByEmail(principal).orElse(null);
        }
    }
}
