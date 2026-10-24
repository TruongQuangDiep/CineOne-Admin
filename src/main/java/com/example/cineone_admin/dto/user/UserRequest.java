package com.example.cineone_admin.dto.user;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRequest {
    @NotNull(message = "Vui lòng chọn hạng thành viên")
    private Long membershipTierId;
}