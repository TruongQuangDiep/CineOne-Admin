package com.example.cineone_admin.controller.api;

import com.example.cineone_admin.dto.common.ApiResponse;
import com.example.cineone_admin.dto.support.CreateSupportRequest;
import com.example.cineone_admin.dto.support.FaqItemResponse;
import com.example.cineone_admin.dto.support.SupportResponse;
import com.example.cineone_admin.service.KnowledgeBaseService;
import com.example.cineone_admin.service.SupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SupportApiController {

    private final SupportService supportService;
    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping("/api/v1/support")
    public ResponseEntity<ApiResponse<SupportResponse>> createSupportRequest(
            @RequestBody CreateSupportRequest request,
            Authentication authentication) {
        SupportResponse data = supportService.createMobileRequest(request, authentication);
        return ResponseEntity.ok(ApiResponse.success(data, "Gửi yêu cầu hỗ trợ / khiếu nại thành công."));
    }

    @GetMapping("/api/v1/support/my-requests")
    public ResponseEntity<ApiResponse<List<SupportResponse>>> getMyRequests(Authentication authentication) {
        List<SupportResponse> data = supportService.getMyRequests(authentication);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách yêu cầu hỗ trợ của bạn thành công."));
    }

    @GetMapping("/api/v1/support/{id}")
    public ResponseEntity<ApiResponse<SupportResponse>> getSupportDetail(
            @PathVariable Long id,
            Authentication authentication) {
        SupportResponse data = supportService.getMyRequestDetail(id, authentication);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy chi tiết yêu cầu hỗ trợ thành công."));
    }

    @GetMapping("/api/v1/faq")
    public ResponseEntity<ApiResponse<List<FaqItemResponse>>> getFaqList(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q) {
        List<FaqItemResponse> data = knowledgeBaseService.getActiveFaqs(category, q);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách câu hỏi thường gặp & chính sách thành công."));
    }
}

