package com.example.cineone_admin.controller.api;

import com.example.cineone_admin.dto.common.ApiResponse;
import com.example.cineone_admin.dto.membership.MyMembershipResponse;
import com.example.cineone_admin.service.MembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/memberships")
@RequiredArgsConstructor
public class MembershipApiController {

    private final MembershipService membershipService;

    @GetMapping("/my-tier")
    public ResponseEntity<ApiResponse<MyMembershipResponse>> getMyMembership() {
        MyMembershipResponse data = membershipService.getMyMembershipMobile();
        return ResponseEntity.ok(ApiResponse.<MyMembershipResponse>builder()
                .status(200)
                .message("Lấy thông tin tiến độ hạng thành viên thành công.")
                .data(data)
                .build());
    }
}