package com.example.cineone_admin.controller;

import com.example.cineone_admin.dto.notification.NotificationCampaignRequest;
import com.example.cineone_admin.entity.NotificationCampaign;
import com.example.cineone_admin.entity.User;
import com.example.cineone_admin.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public String list(@RequestParam(defaultValue = "campaigns") String tab,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String type,
                       @RequestParam(required = false) String targetType,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false) String userQuery,
                       @RequestParam(required = false) String userNotifKeyword,
                       @RequestParam(required = false) String userNotifType,
                       @RequestParam(defaultValue = "0") int userNotifPage,
                       Model model) {

        String activeTab = "user".equalsIgnoreCase(tab) ? "user" : "campaigns";
        Page<NotificationCampaign> campaigns = service.searchCampaigns(keyword, type, targetType, status, page, 10);

        model.addAttribute("activeMenu", "notifications");
        model.addAttribute("tab", activeTab);
        model.addAttribute("campaigns", campaigns);
        model.addAttribute("recipientCounts", service.getRecipientCounts(campaigns.getContent()));
        model.addAttribute("campaignTypes", NotificationService.CAMPAIGN_TYPES);
        model.addAttribute("targetTypes", NotificationService.TARGET_TYPES);
        model.addAttribute("campaignStatuses", NotificationService.CAMPAIGN_STATUSES);
        model.addAttribute("userNotificationTypes", NotificationService.USER_NOTIFICATION_TYPES);

        model.addAttribute("keyword", keyword);
        model.addAttribute("type", type);
        model.addAttribute("targetType", targetType);
        model.addAttribute("status", status);

        model.addAttribute("userQuery", userQuery);
        model.addAttribute("userNotifKeyword", userNotifKeyword);
        model.addAttribute("userNotifType", userNotifType);
        model.addAttribute("userNotifications", service.searchUserNotifications(userNotifKeyword, userNotifType, userNotifPage, 10));

        if (userQuery != null && !userQuery.isBlank()) {
            Optional<User> foundUser = service.findUserByIdentifier(userQuery);
            if (foundUser.isPresent()) {
                model.addAttribute("selectedUser", foundUser.get());
            } else {
                model.addAttribute("userCheckError", "Không tìm thấy người dùng phù hợp với: " + userQuery.trim());
            }
        }

        return "notifications/list";
    }

    @GetMapping("/suggest-users")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> suggestUsers(@RequestParam(name = "q", required = false) String query) {
        return ResponseEntity.ok(service.suggestUsers(query, 8));
    }

    @GetMapping("/check-user")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkUser(@RequestParam(name = "q", required = false) String query) {
        Map<String, Object> body = new HashMap<>();
        List<Map<String, Object>> matches = service.suggestUsers(query, 8);
        if (query == null || query.isBlank() || matches.isEmpty()) {
            body.put("found", false);
            body.put("matches", List.of());
            body.put("message", "Không tìm thấy người dùng phù hợp.");
            return ResponseEntity.ok(body);
        }
        Map<String, Object> first = matches.get(0);
        body.put("found", true);
        body.put("matches", matches);
        body.putAll(first);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/send-user")
    public String sendUserNotification(@RequestParam(required = false) Long userId,
                                       @RequestParam(required = false) String userQuery,
                                       @RequestParam(defaultValue = "SYSTEM") String type,
                                       @RequestParam(required = false) String title,
                                       @RequestParam(required = false) String message,
                                       @RequestParam(defaultValue = "false") boolean sendApp,
                                       @RequestParam(defaultValue = "false") boolean sendEmail,
                                       RedirectAttributes redirectAttributes) {
        try {
            service.sendUserNotification(userId, userQuery, type, title, message, sendApp, sendEmail);
            redirectAttributes.addFlashAttribute("successMessage", "Đã phát thông báo riêng cho người dùng thành công.");
        } catch (ResponseStatusException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getReason());
            redirectAttributes.addFlashAttribute("formUserQuery", userQuery);
            redirectAttributes.addFlashAttribute("formUserType", type);
            redirectAttributes.addFlashAttribute("formUserTitle", title);
            redirectAttributes.addFlashAttribute("formUserMessage", message);
        }
        return "redirect:/notifications?tab=user";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        populateFormModel(model, new NotificationCampaignRequest(), null, false, false);
        return "notifications/form";
    }

    @PostMapping("/create")
    public String create(@Valid @ModelAttribute("campaign") NotificationCampaignRequest request,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        if (bindingResult.hasErrors()) {
            populateFormModel(model, request, null, false, false);
            return "notifications/form";
        }
        try {
            NotificationCampaign created = service.createCampaign(request);
            String msg = "SENT".equalsIgnoreCase(created.getStatus())
                    ? "Đã tạo và phát chiến dịch thông báo thành công."
                    : ("SCHEDULED".equalsIgnoreCase(created.getStatus())
                    ? "Đã lên lịch chiến dịch thông báo thành công."
                    : "Đã lưu nháp chiến dịch thông báo.");
            redirectAttributes.addFlashAttribute("successMessage", msg);
            return "redirect:/notifications";
        } catch (ResponseStatusException ex) {
            model.addAttribute("errorMessage", ex.getReason());
            populateFormModel(model, request, null, false, false);
            return "notifications/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        NotificationCampaign existing = service.getCampaignById(id);
        NotificationCampaignRequest request = toRequest(existing);
        populateFormModel(model, request, id, true, true);
        model.addAttribute("campaignStatus", existing.getStatus());
        model.addAttribute("campaignCreatedAt", existing.getCreatedAt());
        model.addAttribute("campaignNotifications", service.getNotificationsByCampaign(id));
        return "notifications/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        NotificationCampaign existing = service.getCampaignById(id);
        if ("SENT".equalsIgnoreCase(existing.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Chiến dịch đã được phát nên không thể chỉnh sửa.");
            return "redirect:/notifications/" + id;
        }
        NotificationCampaignRequest request = toRequest(existing);
        populateFormModel(model, request, id, true, false);
        model.addAttribute("campaignStatus", existing.getStatus());
        return "notifications/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("campaign") NotificationCampaignRequest request,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        if (bindingResult.hasErrors()) {
            populateFormModel(model, request, id, true, false);
            return "notifications/form";
        }
        try {
            NotificationCampaign updated = service.updateCampaign(id, request);
            String msg = "SENT".equalsIgnoreCase(updated.getStatus())
                    ? "Đã cập nhật và phát chiến dịch thông báo thành công."
                    : "Cập nhật chiến dịch thông báo thành công.";
            redirectAttributes.addFlashAttribute("successMessage", msg);
            return "redirect:/notifications";
        } catch (ResponseStatusException ex) {
            model.addAttribute("errorMessage", ex.getReason());
            populateFormModel(model, request, id, true, false);
            return "notifications/form";
        }
    }

    @PostMapping("/{id}/send")
    public String sendNow(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            service.sendCampaignNow(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã phát chiến dịch thông báo đến đối tượng mục tiêu.");
        } catch (ResponseStatusException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getReason());
        }
        return "redirect:/notifications";
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            service.cancelCampaign(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy chiến dịch thông báo.");
        } catch (ResponseStatusException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getReason());
        }
        return "redirect:/notifications";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            service.deleteCampaign(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa chiến dịch thông báo.");
        } catch (ResponseStatusException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getReason());
        }
        return "redirect:/notifications";
    }

    private NotificationCampaignRequest toRequest(NotificationCampaign entity) {
        String defaultAction = "SCHEDULED".equalsIgnoreCase(entity.getStatus()) ? "SCHEDULE"
                : ("DRAFT".equalsIgnoreCase(entity.getStatus()) ? "DRAFT" : "SEND_NOW");
        return NotificationCampaignRequest.builder()
                .title(entity.getTitle())
                .message(entity.getMessage())
                .type(entity.getType())
                .targetType(entity.getTargetType())
                .scheduledAt(entity.getScheduledAt())
                .status(entity.getStatus())
                .action(defaultAction)
                .build();
    }

    private void populateFormModel(Model model,
                                   NotificationCampaignRequest request,
                                   Long id,
                                   boolean isEdit,
                                   boolean readOnly) {
        model.addAttribute("activeMenu", "notifications");
        model.addAttribute("campaign", request);
        model.addAttribute("id", id);
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("readOnly", readOnly);
        model.addAttribute("campaignTypes", NotificationService.CAMPAIGN_TYPES);
        model.addAttribute("targetTypes", NotificationService.TARGET_TYPES);
    }
}
