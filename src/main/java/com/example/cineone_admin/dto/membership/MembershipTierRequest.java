package com.example.cineone_admin.dto.membership;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class MembershipTierRequest {
    @NotBlank(message = "Vui lòng nhập tên hạng thành viên")
    @Size(max = 30, message = "Tên hạng tối đa 30 ký tự")
    private String name;

    @NotNull(message = "Vui lòng nhập mức chi tiêu tối thiểu")
    @DecimalMin(value = "0.0", inclusive = true, message = "Mức chi tiêu tối thiểu không được âm")
    private BigDecimal minSpending = BigDecimal.ZERO;

    @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
    private String description;

    @Pattern(regexp = "ACTIVE|INACTIVE", message = "Trạng thái chỉ có thể là ACTIVE hoặc INACTIVE")
    private String status = "ACTIVE";
}