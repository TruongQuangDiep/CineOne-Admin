package com.example.cineone_admin.dto.support;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaqItemResponse {
    private Long id;
    private String title;
    private String content;
    private String category;
}

