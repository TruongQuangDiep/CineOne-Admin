package com.example.cineone_admin.dto.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationCampaignRequest {

    @NotBlank(message = "Tiêu đề thông báo không được để trống.")
    @Size(max = 255, message = "Tiêu đề thông báo không được vượt quá 255 ký tự.")
    private String title;

    @NotBlank(message = "Nội dung thông báo không được để trống.")
    private String message;

    @NotBlank(message = "Phân loại chiến dịch không được để trống.")
    @Size(max = 20, message = "Phân loại chiến dịch không được vượt quá 20 ký tự.")
    @Builder.Default
    private String type = "PROMOTION";

    @NotBlank(message = "Đối tượng nhận không được để trống.")
    @Size(max = 20, message = "Đối tượng nhận không được vượt quá 20 ký tự.")
    @Builder.Default
    private String targetType = "ALL_USERS";

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime scheduledAt;

    @Builder.Default
    private String status = "DRAFT";

    @Builder.Default
    private String action = "SEND_NOW";
}
