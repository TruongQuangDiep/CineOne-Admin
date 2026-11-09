package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.membership.MembershipTierRequest;
import com.example.cineone_admin.dto.membership.MyMembershipResponse;
import com.example.cineone_admin.entity.MembershipTier;
import com.example.cineone_admin.entity.User;
import com.example.cineone_admin.repository.BookingRepository;
import com.example.cineone_admin.repository.MembershipTierRepository;
import com.example.cineone_admin.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.*;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipService {

    private final MembershipTierRepository repository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

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

    /**
     * Logic tính toán Tiến độ Hạng thành viên & Quyền lợi cho Mobile (SCRUM-113)
     * Dữ liệu hoàn toàn động từ DB do Admin cấu hình.
     */
    @Transactional
    public MyMembershipResponse getMyMembershipMobile() {
        User user = getAuthenticatedUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để xem thông tin hạng thành viên.");
        }

        // 1. Lấy toàn bộ các hạng thành viên ACTIVE do Admin setup, sắp xếp theo mức chi tiêu tăng dần
        List<MembershipTier> activeTiers = repository.findAll(Sort.by(Sort.Direction.ASC, "minSpending"))
                .stream()
                .filter(t -> "ACTIVE".equalsIgnoreCase(t.getStatus()))
                .toList();

        // 2. Tính tổng chi tiêu thực tế từ các đơn Booking đã thanh toán CONFIRMED
        BigDecimal totalSpent = bookingRepository.calculateTotalSpendingByUserId(user.getUserId());
        if (totalSpent == null) totalSpent = BigDecimal.ZERO;

        // 3. Tự động kiểm tra và nâng hạng User nếu chi tiêu đủ điều kiện
        MembershipTier qualifiedTier = null;
        for (MembershipTier t : activeTiers) {
            if (totalSpent.compareTo(t.getMinSpending()) >= 0) {
                qualifiedTier = t;
            }
        }
        if (qualifiedTier != null && (user.getMembershipTier() == null ||
                !qualifiedTier.getMembershipTierId().equals(user.getMembershipTier().getMembershipTierId()))) {
            user.setMembershipTier(qualifiedTier);
            userRepository.save(user);
        }

        MembershipTier currentTier = user.getMembershipTier() != null ? user.getMembershipTier()
                : (activeTiers.isEmpty() ? null : activeTiers.get(0));

        // 4. Tìm hạng kế tiếp mà User cần phấn đấu đạt tới
        MembershipTier nextTier = null;
        if (currentTier != null) {
            for (MembershipTier t : activeTiers) {
                if (t.getMinSpending().compareTo(currentTier.getMinSpending()) > 0) {
                    nextTier = t;
                    break;
                }
            }
        }

        // 5. Tính toán tiến độ chi tiêu và số tiền còn thiếu
        BigDecimal nextTierMin = nextTier != null ? nextTier.getMinSpending() : totalSpent;
        BigDecimal spendingRemaining = BigDecimal.ZERO;
        double progress = 100.0;

        if (nextTier != null) {
            spendingRemaining = nextTierMin.subtract(totalSpent).max(BigDecimal.ZERO);
            if (nextTierMin.compareTo(BigDecimal.ZERO) > 0) {
                progress = Math.min(100.0, totalSpent.doubleValue() / nextTierMin.doubleValue() * 100.0);
            }
        }

        DecimalFormat currencyFmt = new DecimalFormat("#,### đ");

        // 6. Tách quyền lợi thành các dòng để Mobile hiển thị icon tích ✓
        List<String> benefits = new ArrayList<>();
        if (currentTier != null && currentTier.getDescription() != null && !currentTier.getDescription().isBlank()) {
            benefits = Arrays.stream(currentTier.getDescription().split("\\r?\\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        if (benefits.isEmpty()) {
            benefits = List.of("Tích lũy điểm khi xem phim", "Ưu đãi thành viên CineOne");
        }

        // 7. Chu kỳ thành viên: Từ 01/01 đến 31/12 của năm hiện tại
        int currentYear = LocalDate.now().getYear();
        String cycle = "01/01/" + currentYear + " - 31/12/" + currentYear;

        // 8. Định dạng danh sách toàn bộ các hạng cho popup "Các hạng thành viên" (kèm màu chấm tròn)
        List<String> defaultColors = List.of("#4CAF50", "#2196F3", "#9C27B0", "#FF9800", "#E91E63");
        List<MyMembershipResponse.TierDetailDto> tierDetails = new ArrayList<>();
        for (int i = 0; i < activeTiers.size(); i++) {
            MembershipTier t = activeTiers.get(i);
            boolean isCur = currentTier != null && t.getMembershipTierId().equals(currentTier.getMembershipTierId());
            List<String> bList = t.getDescription() != null ?
                    Arrays.stream(t.getDescription().split("\\r?\\n")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                    : Collections.emptyList();

            tierDetails.add(MyMembershipResponse.TierDetailDto.builder()
                    .tierId(t.getMembershipTierId())
                    .name(t.getName())
                    .minSpending(t.getMinSpending())
                    .minSpendingDisplay("Chi tiêu từ " + currencyFmt.format(t.getMinSpending()))
                    .description(t.getDescription())
                    .benefits(bList)
                    .isCurrent(isCur)
                    .colorHex(defaultColors.get(i % defaultColors.size()))
                    .build());
        }

        return MyMembershipResponse.builder()
                .fullName(user.getFullName())
                .email(user.getEmail())
                .currentTier(currentTier != null ? currentTier.getName() : "NORMAL")
                .totalSpent(totalSpent)
                .totalSpentDisplay(currencyFmt.format(totalSpent))
                .nextTier(nextTier != null ? nextTier.getName() : null)
                .nextTierMinSpending(nextTierMin)
                .nextTierMinSpendingDisplay(currencyFmt.format(nextTierMin))
                .spendingToNextTier(spendingRemaining)
                .spendingToNextTierDisplay(currencyFmt.format(spendingRemaining))
                .progressPercentage(Math.round(progress * 10.0) / 10.0)
                .currentTierBenefits(benefits)
                .membershipCycle(cycle)
                .allTiers(tierDetails)
                .build();
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        String principal = authentication.getName();
        if (principal.contains("@")) {
            return userRepository.findByEmail(principal).orElse(null);
        }
        try {
            Long userId = Long.parseLong(principal);
            return userRepository.findById(userId).orElse(null);
        } catch (NumberFormatException e) {
            return userRepository.findByEmail(principal).orElse(null);
        }
    }
}