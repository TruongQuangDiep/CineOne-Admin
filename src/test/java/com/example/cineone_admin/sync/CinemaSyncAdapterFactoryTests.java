package com.example.cineone_admin.sync;

import com.example.cineone_admin.dto.sync.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CinemaSyncAdapterFactoryTests {
    private CinemaSyncAdapter adapter(String code) {
        return new CinemaSyncAdapter() {
            public String providerCode() { return code; }
            public SyncResultDTO sync(SyncRequestDTO request) { return SyncResultDTO.of(code, NormalizedCatalogDTO.empty(), List.of()); }
        };
    }

    @Test void selectsRegisteredStrategy() {
        var cgv = adapter("CGV");
        var lotte = adapter("LOTTE");
        var factory = new CinemaSyncAdapterFactory(List.of(cgv, lotte));
        assertSame(cgv, factory.getAdapter("CGV"));
        assertSame(lotte, factory.getAdapter("LOTTE"));
    }

    @Test void rejectsDuplicateAndMissingStrategies() {
        assertThrows(IllegalStateException.class,
                () -> new CinemaSyncAdapterFactory(List.of(adapter("CGV"), adapter("CGV"))));
        var factory = new CinemaSyncAdapterFactory(List.of());
        assertThrows(IllegalArgumentException.class, () -> factory.getAdapter("UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> factory.getAdapter(null));
        assertThrows(IllegalArgumentException.class,
                () -> new CinemaSyncAdapterFactory(List.of(adapter(" "))));
    }
}
