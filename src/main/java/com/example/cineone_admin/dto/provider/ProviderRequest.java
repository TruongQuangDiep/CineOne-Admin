package com.example.cineone_admin.dto.provider;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.util.Locale;

@Getter
@Setter
public class ProviderRequest {
    @NotBlank(message = "Vui lòng nhập mã nhà cung cấp")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}", message = "Mã tối đa 50 chữ in hoa, số hoặc dấu _, bắt đầu bằng chữ")
    private String code;
    @NotBlank(message = "Vui lòng nhập tên nhà cung cấp")
    @Size(max = 150, message = "Tên tối đa 150 ký tự")
    private String name;
    @NotBlank(message = "Vui lòng nhập loại nhà cung cấp")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}", message = "Loại tối đa 50 chữ in hoa, số hoặc dấu _")
    private String providerType = "CINEMA";
    private boolean mock;

    public void setCode(String code) { this.code = normalize(code); }
    public void setName(String name) { this.name = name == null ? null : name.strip(); }
    public void setProviderType(String type) { this.providerType = normalize(type); }
    private static String normalize(String value) {
        return value == null ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
