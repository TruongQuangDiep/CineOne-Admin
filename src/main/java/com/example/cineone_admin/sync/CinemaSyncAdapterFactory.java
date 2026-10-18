package com.example.cineone_admin.sync;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Đăng ký các chiến lược Adapter; thêm bean cho nhà cung cấp mới mà không cần sửa điều kiện xử lý trong phần lõi. */
@Component
public class CinemaSyncAdapterFactory {
    private final Map<String, CinemaSyncAdapter> adapters;

    public CinemaSyncAdapterFactory(List<CinemaSyncAdapter> strategies) {
        Map<String, CinemaSyncAdapter> registry = new HashMap<>();
        for (CinemaSyncAdapter adapter : strategies) {
            String code = adapter.providerCode();
            if (code == null || code.isBlank() || code.length() > 50)
                throw new IllegalArgumentException("Mã nhà cung cấp của Adapter không hợp lệ");
            if (registry.putIfAbsent(code, adapter) != null)
                throw new IllegalStateException("Có nhiều Adapter đăng ký cho cùng nhà cung cấp: " + code);
        }
        adapters = Map.copyOf(registry);
    }

    public CinemaSyncAdapter getAdapter(String providerCode) {
        if (providerCode == null || providerCode.isBlank())
            throw new IllegalArgumentException("Mã nhà cung cấp là bắt buộc");
        CinemaSyncAdapter adapter = adapters.get(providerCode);
        if (adapter == null) throw new IllegalArgumentException("Chưa đăng ký Adapter cho nhà cung cấp: " + providerCode);
        return adapter;
    }
}
