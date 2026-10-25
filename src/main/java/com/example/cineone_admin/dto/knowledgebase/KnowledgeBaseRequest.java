package com.example.cineone_admin.dto.knowledgebase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeBaseRequest {

    @NotBlank(message = "Tiêu đề không được để trống.")
    @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự.")
    private String title;

    @NotBlank(message = "Danh mục không được để trống.")
    @Size(max = 20, message = "Danh mục không được vượt quá 20 ký tự.")
    private String category;

    @NotBlank(message = "Nội dung bài viết không được để trống.")
    private String content;

    @NotBlank(message = "Trạng thái không được để trống.")
    @Size(max = 10, message = "Trạng thái không được vượt quá 10 ký tự.")
    @Builder.Default
    private String status = "ACTIVE";
}