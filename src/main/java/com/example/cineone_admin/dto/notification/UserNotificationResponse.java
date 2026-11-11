package com.example.cineone_admin.dto.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNotificationResponse {
    private Long notificationId;
    private Long campaignId;
    private String title;
    private String message;
    private String type;

    @JsonProperty("isRead")
    private boolean isRead;

    private String createdAt;
}