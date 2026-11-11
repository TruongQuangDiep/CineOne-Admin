package com.example.cineone_admin.dto.support;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSupportRequest {
    private String type;
    private String category;
    private String content;
    private Long bookingId;
    private String bookingCode;
}

