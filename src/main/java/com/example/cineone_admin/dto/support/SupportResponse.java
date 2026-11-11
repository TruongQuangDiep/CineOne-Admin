package com.example.cineone_admin.dto.support;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportResponse {
    private Long requestId;
    private String type;
    private String category;
    private String content;
    private String status;
    private String response;
    private Long bookingId;
    private String bookingCode;
    private String respondedAt;
    private String createdAt;
}

