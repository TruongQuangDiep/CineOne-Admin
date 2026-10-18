package com.example.cineone_admin.service;

import com.example.cineone_admin.entity.Cinema;
import com.example.cineone_admin.enums.ProviderEntityType;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderMappingRetryTests {
    private final ProviderMappingTransactionService transactions = mock(ProviderMappingTransactionService.class);
    private final ProviderMappingService service = new ProviderMappingService(transactions);

    private RuntimeException conflict(String constraint) {
        return new ConstraintViolationException("Vi phạm ràng buộc duy nhất", new SQLException("Trùng dữ liệu", "23505"), constraint);
    }

    @Test void retriesMappingUniqueConflictAfterFailedTransactionReturns() {
        when(transactions.resolveOrCreate(eq(1L), eq(ProviderEntityType.CINEMA), eq("x"), any()))
                .thenThrow(conflict("uq_provider_external")).thenReturn(9L);
        assertEquals(9L, service.resolveOrCreate(1L, ProviderEntityType.CINEMA, "x", Cinema::new));
        verify(transactions, times(2)).resolveOrCreate(eq(1L), eq(ProviderEntityType.CINEMA), eq("x"), any());
    }

    @Test void limitsAttemptsAndDoesNotRetryOtherConstraints() {
        when(transactions.resolveOrCreate(eq(1L), eq(ProviderEntityType.CINEMA), eq("x"), any()))
                .thenThrow(conflict("uq_provider_external"));
        assertThrows(RuntimeException.class, () -> service.resolveOrCreate(1L, ProviderEntityType.CINEMA, "x", Cinema::new));
        verify(transactions, times(3)).resolveOrCreate(eq(1L), eq(ProviderEntityType.CINEMA), eq("x"), any());
        reset(transactions);
        when(transactions.resolveOrCreate(eq(1L), eq(ProviderEntityType.CINEMA), eq("x"), any()))
                .thenThrow(conflict("uq_cinema_brand_name"));
        assertThrows(RuntimeException.class, () -> service.resolveOrCreate(1L, ProviderEntityType.CINEMA, "x", Cinema::new));
        verify(transactions).resolveOrCreate(eq(1L), eq(ProviderEntityType.CINEMA), eq("x"), any());
    }

    @Test void leavesRetryOfExistingOuterTransactionToCaller() {
        when(transactions.resolveOrCreate(eq(1L), eq(ProviderEntityType.CINEMA), eq("x"), any()))
                .thenThrow(conflict("uq_provider_external"));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(RuntimeException.class, () -> service.resolveOrCreate(1L, ProviderEntityType.CINEMA, "x", Cinema::new));
            verify(transactions).resolveOrCreate(eq(1L), eq(ProviderEntityType.CINEMA), eq("x"), any());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
    @Test void recoversNaturalKeyRaceOnlyWhenExactRequestedMappingWasPublished() {
        when(transactions.resolveOrCreate(eq(1L), eq(ProviderEntityType.CINEMA), eq("x"), any()))
                .thenThrow(conflict("uq_cinema_brand_name")).thenReturn(9L);
        when(transactions.findInternalId(1L, ProviderEntityType.CINEMA, "x"))
                .thenReturn(java.util.Optional.of(9L));
        assertEquals(9L, service.resolveOrCreate(1L, ProviderEntityType.CINEMA, "x", Cinema::new));
        verify(transactions, times(2)).resolveOrCreate(eq(1L), eq(ProviderEntityType.CINEMA), eq("x"), any());
    }
}
