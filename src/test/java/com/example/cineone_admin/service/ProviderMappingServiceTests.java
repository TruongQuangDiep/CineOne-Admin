package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.sync.EntityCreationPair;
import com.example.cineone_admin.entity.*;
import com.example.cineone_admin.enums.ProviderEntityType;
import com.example.cineone_admin.exception.ProviderMappingException;
import com.example.cineone_admin.repository.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderMappingServiceTests {
    private final ProviderMappingRepository mappings = mock(ProviderMappingRepository.class);
    private final DataProviderRepository providers = mock(DataProviderRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final RoomTypeCatalogService roomTypes = mock(RoomTypeCatalogService.class);
    private final ProviderMappingTransactionService service = new ProviderMappingTransactionService(mappings, providers, em, roomTypes);
    private static final ProviderEntityType TYPE = ProviderEntityType.CINEMA;

    @Test void rejectsDuplicateExternalIdsBeforeInvokingFactories() {
        Supplier<Cinema> create = mock(Supplier.class);
        assertThrows(IllegalArgumentException.class, () -> service.resolveOrCreateBatch(1L, TYPE,
                List.of(new EntityCreationPair<>("x", create), new EntityCreationPair<>("x", create))));
        verifyNoInteractions(create, mappings, providers, em);
    }

    @Test void rejectsOversizedWriteBatchBeforeOpeningLocks() {
        var items = IntStream.range(0, 501).mapToObj(i -> new EntityCreationPair<>("id-" + i, Cinema::new)).toList();
        assertThrows(IllegalArgumentException.class, () -> service.resolveOrCreateBatch(1L, TYPE, items));
        verifyNoInteractions(mappings, providers, em);
    }

    @Test void emptyBatchesDoNotAccessDatabase() {
        assertTrue(service.resolveOrCreateBatch(1L, TYPE, List.of()).isEmpty());
        assertTrue(service.findInternalIds(1L, TYPE, List.of()).isEmpty());
        assertTrue(service.bindBatch(1L, TYPE, Map.of()).isEmpty());
        verifyNoInteractions(mappings, providers, em);
    }

    @Test void missingProviderFailsBeforeConstructingEntity() {
        Supplier<Cinema> create = mock(Supplier.class);
        assertThrows(ProviderMappingException.class, () -> service.resolveOrCreate(1L, TYPE, "x", create));
        verifyNoInteractions(create);
    }

    @Test void rejectsAmbiguousInternalIdsInBindBatch() {
        assertThrows(ProviderMappingException.class,
                () -> service.bindBatch(1L, TYPE, Map.of("x", 1L, "y", 1L)));
        verifyNoInteractions(mappings, providers, em);
    }

    @Test void readLookupSplitsLargeRequestsAndIncludesProviderAndType() {
        var ids = IntStream.range(0, 1200).mapToObj(i -> "id-" + i).toList();
        assertTrue(service.findInternalIds(2L, ProviderEntityType.ROOM, ids).isEmpty());
        verify(mappings, times(3)).findByProviderProviderIdAndEntityTypeAndExternalIdIn(eq(2L), eq("ROOM"), anyCollection());
        verifyNoInteractions(em, providers);
    }

    @Test void rejectsInvalidIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> service.findInternalId(1L, TYPE, " "));
        assertThrows(IllegalArgumentException.class, () -> service.findExternalId(1L, TYPE, 0L));
        assertThrows(IllegalArgumentException.class, () -> service.findInternalIds(0L, TYPE, List.of("x")));
        verifyNoInteractions(mappings, providers, em);
    }
    @Test void rejectsDatabaseCollationAliasInsteadOfGuessingIdentifier() {
        var mapping = new ProviderMapping();
        mapping.setExternalId("abc");
        mapping.setInternalId(1L);
        when(mappings.findByProviderProviderIdAndEntityTypeAndExternalIdIn(eq(1L), eq("CINEMA"), anyCollection()))
                .thenReturn(List.of(mapping));
        assertThrows(ProviderMappingException.class, () -> service.findInternalIds(1L, TYPE, List.of("ABC")));
        verifyNoInteractions(em);
    }
}
