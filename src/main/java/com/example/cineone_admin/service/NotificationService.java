package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.notification.NotificationCampaignRequest;
import com.example.cineone_admin.entity.Notification;
import com.example.cineone_admin.entity.NotificationCampaign;
import com.example.cineone_admin.entity.User;
import com.example.cineone_admin.repository.NotificationCampaignRepository;
import com.example.cineone_admin.repository.NotificationRepository;
import com.example.cineone_admin.repository.UserRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationCampaignRepository campaignRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public static final List<String> CAMPAIGN_TYPES = List.of("PROMOTION", "SYSTEM", "REMINDER");
    public static final List<String> TARGET_TYPES = List.of("ALL_USERS", "NORMAL", "VIP", "PLATINUM");
    public static final List<String> CAMPAIGN_STATUSES = List.of("DRAFT", "SCHEDULED", "SENT", "CANCELLED", "FAILED");
    public static final List<String> USER_NOTIFICATION_TYPES = List.of("SYSTEM", "PROMOTION", "REMINDER", "BOOKING", "PAYMENT", "TICKET");

    public Page<NotificationCampaign> searchCampaigns(String keyword, String type, String targetType, String status, int page, int size) {
        return campaignRepository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
                Predicate messageMatch = cb.like(cb.lower(root.get("message")), pattern);
                predicates.add(cb.or(titleMatch, messageMatch));
            }

            if (type != null && !type.isBlank()) {
                predicates.add(cb.equal(root.get("type"), type.trim().toUpperCase(Locale.ROOT)));
            }

            if (targetType != null && !targetType.isBlank()) {
                predicates.add(cb.equal(root.get("targetType"), targetType.trim().toUpperCase(Locale.ROOT)));
            }

            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim().toUpperCase(Locale.ROOT)));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        }, PageRequest.of(Math.max(0, page), size, Sort.by(Sort.Direction.DESC, "campaignId")));
    }

    public NotificationCampaign getCampaignById(Long id) {
        return campaignRepository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chiến dịch thông báo với ID: " + id));
    }

    public Map<Long, Long> getRecipientCounts(Collection<NotificationCampaign> campaigns) {
        Map<Long, Long> counts = new HashMap<>();
        if (campaigns == null) {
            return counts;
        }
        for (NotificationCampaign c : campaigns) {
            if (c.getCampaignId() != null) {
                counts.put(c.getCampaignId(), notificationRepository.countByCampaign_CampaignId(c.getCampaignId()));
            }
        }
        return counts;
    }

    public List<Notification> getNotificationsByCampaign(Long campaignId) {
        List<Notification> list = notificationRepository.findByCampaign_CampaignIdOrderByNotificationIdDesc(campaignId);
        for (Notification n : list) {
            if (n.getUser() != null) {
                n.getUser().getFullName();
                n.getUser().getEmail();
            }
        }
        return list;
    }

    public Page<Notification> searchUserNotifications(String keyword, String type, int page, int size) {
        return notificationRepository.findAll((root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                var userFetch = root.fetch("user", JoinType.LEFT);
                userFetch.fetch("membershipTier", JoinType.LEFT);
                root.fetch("campaign", JoinType.LEFT);
            }
            var predicates = new ArrayList<Predicate>();

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                var userJoin = root.join("user", JoinType.LEFT);
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
                Predicate messageMatch = cb.like(cb.lower(root.get("message")), pattern);
                Predicate emailMatch = cb.like(cb.lower(userJoin.get("email")), pattern);
                Predicate nameMatch = cb.like(cb.lower(userJoin.get("fullName")), pattern);
                predicates.add(cb.or(titleMatch, messageMatch, emailMatch, nameMatch));
            }

            if (type != null && !type.isBlank()) {
                predicates.add(cb.equal(root.get("type"), type.trim().toUpperCase(Locale.ROOT)));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        }, PageRequest.of(Math.max(0, page), size, Sort.by(Sort.Direction.DESC, "notificationId")));
    }

    @Transactional
    public NotificationCampaign createCampaign(NotificationCampaignRequest request) {
        validateCampaignRequest(request);

        String action = normalizeAction(request.getAction(), request.getScheduledAt());

        NotificationCampaign campaign = new NotificationCampaign();
        campaign.setTitle(request.getTitle().trim());
        campaign.setMessage(request.getMessage().trim());
        campaign.setType(request.getType().trim().toUpperCase(Locale.ROOT));
        campaign.setTargetType(request.getTargetType().trim().toUpperCase(Locale.ROOT));

        if ("SCHEDULE".equals(action)) {
            if (request.getScheduledAt() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn thời điểm dự kiến gửi khi lên lịch.");
            }
            campaign.setScheduledAt(request.getScheduledAt());
            campaign.setStatus("SCHEDULED");
            return campaignRepository.saveAndFlush(campaign);
        } else if ("DRAFT".equals(action)) {
            campaign.setScheduledAt(request.getScheduledAt());
            campaign.setStatus("DRAFT");
            return campaignRepository.saveAndFlush(campaign);
        } else {
            campaign.setScheduledAt(request.getScheduledAt() != null ? request.getScheduledAt() : LocalDateTime.now());
            campaign.setStatus("DRAFT");
            NotificationCampaign saved = campaignRepository.saveAndFlush(campaign);
            return dispatchCampaign(saved);
        }
    }

    @Transactional
    public NotificationCampaign updateCampaign(Long id, NotificationCampaignRequest request) {
        NotificationCampaign campaign = getCampaignById(id);
        if ("SENT".equalsIgnoreCase(campaign.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể chỉnh sửa chiến dịch đã được phát.");
        }

        validateCampaignRequest(request);
        String action = normalizeAction(request.getAction(), request.getScheduledAt());

        campaign.setTitle(request.getTitle().trim());
        campaign.setMessage(request.getMessage().trim());
        campaign.setType(request.getType().trim().toUpperCase(Locale.ROOT));
        campaign.setTargetType(request.getTargetType().trim().toUpperCase(Locale.ROOT));

        if ("SCHEDULE".equals(action)) {
            if (request.getScheduledAt() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn thời điểm dự kiến gửi khi lên lịch.");
            }
            campaign.setScheduledAt(request.getScheduledAt());
            campaign.setStatus("SCHEDULED");
            return campaignRepository.saveAndFlush(campaign);
        } else if ("SEND_NOW".equals(action)) {
            campaign.setScheduledAt(LocalDateTime.now());
            NotificationCampaign saved = campaignRepository.saveAndFlush(campaign);
            return dispatchCampaign(saved);
        } else {
            campaign.setScheduledAt(request.getScheduledAt());
            campaign.setStatus("DRAFT");
            return campaignRepository.saveAndFlush(campaign);
        }
    }

    @Transactional
    public NotificationCampaign sendCampaignNow(Long id) {
        NotificationCampaign campaign = getCampaignById(id);
        if ("SENT".equalsIgnoreCase(campaign.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chiến dịch này đã được phát trước đó.");
        }
        if ("CANCELLED".equalsIgnoreCase(campaign.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể phát chiến dịch đã hủy.");
        }
        if (campaign.getScheduledAt() == null) {
            campaign.setScheduledAt(LocalDateTime.now());
        }
        return dispatchCampaign(campaign);
    }

    @Transactional
    public NotificationCampaign cancelCampaign(Long id) {
        NotificationCampaign campaign = getCampaignById(id);
        if ("SENT".equalsIgnoreCase(campaign.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể hủy chiến dịch đã được phát.");
        }
        campaign.setStatus("CANCELLED");
        return campaignRepository.saveAndFlush(campaign);
    }

    @Transactional
    public void deleteCampaign(Long id) {
        NotificationCampaign campaign = getCampaignById(id);
        campaignRepository.delete(campaign);
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void processScheduledCampaigns() {
        List<NotificationCampaign> dueCampaigns = campaignRepository
                .findByStatusAndScheduledAtLessThanEqual("SCHEDULED", LocalDateTime.now());
        for (NotificationCampaign campaign : dueCampaigns) {
            try {
                dispatchCampaign(campaign);
            } catch (Exception e) {
                campaign.setStatus("FAILED");
                campaignRepository.save(campaign);
            }
        }
    }

    protected NotificationCampaign dispatchCampaign(NotificationCampaign campaign) {
        try {
            List<User> targetUsers = findUsersForTargetType(campaign.getTargetType());
            List<Notification> notifications = new ArrayList<>(targetUsers.size());

            for (User user : targetUsers) {
                Notification n = new Notification();
                n.setUser(user);
                n.setCampaign(campaign);
                n.setTitle(campaign.getTitle());
                n.setMessage(campaign.getMessage());
                n.setType(campaign.getType());
                n.setRead(false);
                notifications.add(n);
            }

            if (!notifications.isEmpty()) {
                notificationRepository.saveAll(notifications);
            }
            campaign.setStatus("SENT");
            return campaignRepository.saveAndFlush(campaign);
        } catch (Exception ex) {
            campaign.setStatus("FAILED");
            campaignRepository.saveAndFlush(campaign);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Phát chiến dịch thông báo thất bại: " + ex.getMessage());
        }
    }

    public List<User> findUsersForTargetType(String targetType) {
        String normTarget = targetType == null ? "ALL_USERS" : targetType.trim().toUpperCase(Locale.ROOT);
        List<User> allUsers = userRepository.findAll((root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("membershipTier", JoinType.LEFT);
            }
            return cb.notEqual(cb.upper(root.get("status")), "LOCKED");
        });

        if ("ALL_USERS".equals(normTarget)) {
            return allUsers;
        }

        return allUsers.stream()
                .filter(u -> matchesMembershipTarget(u, normTarget))
                .toList();
    }

    private boolean matchesMembershipTarget(User user, String targetType) {
        String tierName = user.getMembershipTier() != null && user.getMembershipTier().getName() != null
                ? user.getMembershipTier().getName().trim().toUpperCase(Locale.ROOT)
                : "NORMAL";
        if ("NORMAL".equals(targetType)) {
            return "NORMAL".equals(tierName) || "STANDARD".equals(tierName) || "MEMBER".equals(tierName) || "THƯỜNG".equals(tierName);
        }
        return targetType.equalsIgnoreCase(tierName);
    }

    public List<Map<String, Object>> suggestUsers(String rawQuery, int limit) {
        int maxResults = Math.max(1, Math.min(limit, 20));
        List<User> allUsers = userRepository.findAll((root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("membershipTier", JoinType.LEFT);
            }
            return cb.conjunction();
        }, Sort.by(Sort.Direction.ASC, "userId"));

        String q = rawQuery == null ? "" : rawQuery.trim();
        if (q.isEmpty()) {
            return allUsers.stream()
                    .sorted(Comparator.comparing((User u) -> "ADMIN".equalsIgnoreCase(u.getRole()) ? 1 : 0)
                            .thenComparing(User::getUserId))
                    .limit(maxResults)
                    .map(this::toUserSummaryMap)
                    .toList();
        }

        return allUsers.stream()
                .filter(u -> matchesUserQuery(u, q))
                .sorted(Comparator.comparingInt((User u) -> scoreUserMatch(u, q))
                        .thenComparing(User::getUserId))
                .limit(maxResults)
                .map(this::toUserSummaryMap)
                .toList();
    }

    public Optional<User> findUserByIdentifier(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return Optional.empty();
        }
        String q = rawQuery.trim();

        List<User> allUsers = userRepository.findAll((root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("membershipTier", JoinType.LEFT);
            }
            return cb.conjunction();
        }, Sort.by(Sort.Direction.ASC, "userId"));

        return allUsers.stream()
                .filter(u -> matchesUserQuery(u, q))
                .min(Comparator.comparingInt((User u) -> scoreUserMatch(u, q))
                        .thenComparing(User::getUserId));
    }

    public Map<String, Object> toUserSummaryMap(User u) {
        Map<String, Object> item = new LinkedHashMap<>();
        String tierName = u.getMembershipTier() != null && u.getMembershipTier().getName() != null
                ? u.getMembershipTier().getName()
                : "NORMAL";
        item.put("userId", u.getUserId());
        item.put("fullName", u.getFullName() != null ? u.getFullName() : "—");
        item.put("email", u.getEmail() != null ? u.getEmail() : "—");
        item.put("phone", u.getPhone() != null && !u.getPhone().isBlank() ? u.getPhone() : "Chưa cập nhật SĐT");
        item.put("tier", tierName);
        item.put("role", u.getRole());
        item.put("status", u.getStatus());
        return item;
    }

    private boolean matchesUserQuery(User u, String rawQuery) {
        String qLower = rawQuery.trim().toLowerCase(Locale.ROOT);
        String qFolded = foldText(rawQuery);
        String qPhone = normalizePhone(rawQuery);

        String idCandidate = qLower.startsWith("#") ? qLower.substring(1).trim() : qLower;
        if (u.getUserId() != null && String.valueOf(u.getUserId()).equals(idCandidate)) {
            return true;
        }

        if (!qPhone.isEmpty() && u.getPhone() != null && !u.getPhone().isBlank()) {
            String userPhone = normalizePhone(u.getPhone());
            if (userPhone.contains(qPhone) || qPhone.contains(userPhone)) {
                return true;
            }
        }

        if (u.getEmail() != null && u.getEmail().toLowerCase(Locale.ROOT).contains(qLower)) {
            return true;
        }

        if (u.getFullName() != null) {
            String nameLower = u.getFullName().toLowerCase(Locale.ROOT);
            String nameFolded = foldText(u.getFullName());
            if (nameLower.contains(qLower) || (!qFolded.isEmpty() && nameFolded.contains(qFolded))) {
                return true;
            }
        }

        return false;
    }

    private int scoreUserMatch(User u, String rawQuery) {
        String qLower = rawQuery.trim().toLowerCase(Locale.ROOT);
        String qPhone = normalizePhone(rawQuery);
        String userPhone = u.getPhone() != null ? normalizePhone(u.getPhone()) : "";

        if (!qPhone.isEmpty() && !userPhone.isEmpty() && userPhone.equals(qPhone)) {
            return 0;
        }
        if (u.getEmail() != null && u.getEmail().equalsIgnoreCase(qLower)) {
            return 1;
        }
        if (u.getUserId() != null && String.valueOf(u.getUserId()).equals(qLower)) {
            return 2;
        }
        if (!qPhone.isEmpty() && !userPhone.isEmpty() && userPhone.startsWith(qPhone)) {
            return 3;
        }
        if (u.getEmail() != null && u.getEmail().toLowerCase(Locale.ROOT).startsWith(qLower)) {
            return 4;
        }
        return 5;
    }

    private static String normalizePhone(String input) {
        if (input == null) {
            return "";
        }
        String digits = input.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+84")) {
            digits = "0" + digits.substring(3);
        } else if (digits.startsWith("84") && digits.length() >= 11) {
            digits = "0" + digits.substring(2);
        }
        return digits.replaceAll("[^0-9]", "");
    }

    private static String foldText(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    @Transactional
    public Notification sendUserNotification(Long userId,
                                             String userQuery,
                                             String type,
                                             String title,
                                             String message,
                                             boolean sendApp,
                                             boolean sendEmail) {
        User recipient = null;
        if (userId != null && userId > 0) {
            recipient = userRepository.findById(userId).orElse(null);
        }
        if (recipient == null && userQuery != null && !userQuery.isBlank()) {
            recipient = findUserByIdentifier(userQuery).orElse(null);
        }
        if (recipient == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy người nhận. Vui lòng chọn một người dùng từ danh sách gợi ý hoặc nhập đúng SĐT / Email.");
        }

        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tiêu đề thông báo không được để trống.");
        }
        if (title.trim().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tiêu đề thông báo không được vượt quá 255 ký tự.");
        }
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung chi tiết không được để trống.");
        }

        String normType = type == null || type.isBlank() ? "SYSTEM" : type.trim().toUpperCase(Locale.ROOT);
        if (!USER_NOTIFICATION_TYPES.contains(normType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phân loại thông báo không hợp lệ.");
        }

        if (!sendApp && !sendEmail) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn ít nhất một tùy chọn gửi (Ứng dụng hoặc Gmail).");
        }

        Notification savedNotification = null;
        if (sendApp) {
            Notification notification = new Notification();
            notification.setUser(recipient);
            notification.setCampaign(null);
            notification.setTitle(title.trim());
            notification.setMessage(message.trim());
            notification.setType(normType);
            notification.setRead(false);
            savedNotification = notificationRepository.saveAndFlush(notification);
        }

        if (sendEmail) {
            emailService.sendSystemNotificationEmail(
                    recipient.getEmail(),
                    recipient.getFullName(),
                    title.trim(),
                    message.trim(),
                    normType
            );
        }

        return savedNotification;
    }

    private void validateCampaignRequest(NotificationCampaignRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dữ liệu chiến dịch không hợp lệ.");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tiêu đề thông báo không được để trống.");
        }
        if (request.getTitle().trim().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tiêu đề thông báo không được vượt quá 255 ký tự.");
        }
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung thông báo không được để trống.");
        }
        String normType = request.getType() != null ? request.getType().trim().toUpperCase(Locale.ROOT) : "";
        if (!CAMPAIGN_TYPES.contains(normType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phân loại chiến dịch không hợp lệ.");
        }
        String normTarget = request.getTargetType() != null ? request.getTargetType().trim().toUpperCase(Locale.ROOT) : "";
        if (!TARGET_TYPES.contains(normTarget)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đối tượng nhận thông báo không hợp lệ.");
        }
    }

    private String normalizeAction(String action, LocalDateTime scheduledAt) {
        if (action != null && !action.isBlank()) {
            String upper = action.trim().toUpperCase(Locale.ROOT);
            if ("SCHEDULE".equals(upper) || "SCHEDULED".equals(upper)) {
                return "SCHEDULE";
            }
            if ("DRAFT".equals(upper)) {
                return "DRAFT";
            }
            if ("SEND_NOW".equals(upper) || "SEND".equals(upper)) {
                return "SEND_NOW";
            }
        }
        if (scheduledAt != null && scheduledAt.isAfter(LocalDateTime.now())) {
            return "SCHEDULE";
        }
        return "SEND_NOW";
    }
}
