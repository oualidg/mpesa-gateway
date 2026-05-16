/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/16/2026 at 8:24 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.service;

import com.mycompany.api.mpesa.document.MpesaEvent;
import com.mycompany.api.mpesa.enums.MpesaEventState;
import com.mycompany.api.mpesa.messaging.ProvisioningResultMessage;
import com.mycompany.api.mpesa.repository.MpesaEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ResultProcessingService}.
 *
 * <p>Verifies state transitions — POSTED on success, SUSPENDED on failure,
 * guard against overwriting POSTED, and exception when event is not found.
 *
 * @author Oualid Gharach
 */
@ExtendWith(MockitoExtension.class)
class ResultProcessingServiceTest {

    @Mock
    private MpesaEventRepository mpesaEventRepository;

    @InjectMocks
    private ResultProcessingService resultProcessingService;

    private MpesaEvent event;

    @BeforeEach
    void setUp() {
        event = new MpesaEvent();
        event.setTransId("NLJ7RT61SV");
        event.setAmount(new BigDecimal("1500.00"));
        event.setState(MpesaEventState.RECEIVED);
    }

    // =========================================================================
    // Success — POSTED
    // =========================================================================

    @Test
    @DisplayName("Transitions event to POSTED with billingReceipt on success result")
    void shouldTransitionEventToPostedOnSuccessResult() {
        when(mpesaEventRepository.findByTransId("NLJ7RT61SV")).thenReturn(Optional.of(event));
        var result = new ProvisioningResultMessage(
                UUID.randomUUID(), "NLJ7RT61SV", true, "receipt-123", null);

        resultProcessingService.process(result);

        var captor = ArgumentCaptor.forClass(MpesaEvent.class);
        verify(mpesaEventRepository).save(captor.capture());
        assertThat(captor.getValue().getState()).isEqualTo(MpesaEventState.POSTED);
        assertThat(captor.getValue().getBillingReceipt()).isEqualTo("receipt-123");
        assertThat(captor.getValue().getPostedAt()).isNotNull();
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
        assertThat(captor.getValue().getFailureReason()).isNull();
    }

    // =========================================================================
    // Failure — SUSPENDED
    // =========================================================================

    @Test
    @DisplayName("Transitions event to SUSPENDED with failureReason on failure result")
    void shouldTransitionEventToSuspendedOnFailureResult() {
        when(mpesaEventRepository.findByTransId("NLJ7RT61SV")).thenReturn(Optional.of(event));
        var result = new ProvisioningResultMessage(
                UUID.randomUUID(), "NLJ7RT61SV", false, null, "Account not found");

        resultProcessingService.process(result);

        var captor = ArgumentCaptor.forClass(MpesaEvent.class);
        verify(mpesaEventRepository).save(captor.capture());
        assertThat(captor.getValue().getState()).isEqualTo(MpesaEventState.SUSPENDED);
        assertThat(captor.getValue().getFailureReason()).isEqualTo("Account not found");
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
        assertThat(captor.getValue().getBillingReceipt()).isNull();
    }

    // =========================================================================
    // Ops Recovery — SUSPENDED → POSTED
    // =========================================================================

    @Test
    @DisplayName("Allows SUSPENDED to POSTED transition for ops recovery and clears failureReason")
    void shouldAllowSuspendedToPostedTransitionForOpsRecovery() {
        event.setState(MpesaEventState.SUSPENDED);
        event.setFailureReason("Previous failure");
        when(mpesaEventRepository.findByTransId("NLJ7RT61SV")).thenReturn(Optional.of(event));
        var result = new ProvisioningResultMessage(
                UUID.randomUUID(), "NLJ7RT61SV", true, "receipt-123", null);

        resultProcessingService.process(result);

        var captor = ArgumentCaptor.forClass(MpesaEvent.class);
        verify(mpesaEventRepository).save(captor.capture());
        assertThat(captor.getValue().getState()).isEqualTo(MpesaEventState.POSTED);
        assertThat(captor.getValue().getBillingReceipt()).isEqualTo("receipt-123");
        assertThat(captor.getValue().getFailureReason()).isNull();
    }

    // =========================================================================
    // Guard — POSTED is terminal
    // =========================================================================

    @Test
    @DisplayName("Ignores result when event is already POSTED — POSTED is terminal")
    void shouldIgnoreResultWhenEventAlreadyPosted() {
        event.setState(MpesaEventState.POSTED);
        event.setBillingReceipt("existing-receipt");
        when(mpesaEventRepository.findByTransId("NLJ7RT61SV")).thenReturn(Optional.of(event));
        var result = new ProvisioningResultMessage(
                UUID.randomUUID(), "NLJ7RT61SV", true, "new-receipt", null);

        resultProcessingService.process(result);

        verify(mpesaEventRepository, never()).save(any());
    }

    // =========================================================================
    // Event Not Found
    // =========================================================================

    @Test
    @DisplayName("Throws IllegalStateException when event is not found")
    void shouldThrowWhenEventNotFound() {
        when(mpesaEventRepository.findByTransId("NLJ7RT61SV")).thenReturn(Optional.empty());
        var result = new ProvisioningResultMessage(
                UUID.randomUUID(), "NLJ7RT61SV", true, "receipt-123", null);

        assertThatThrownBy(() -> resultProcessingService.process(result))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NLJ7RT61SV");

        verify(mpesaEventRepository, never()).save(any());
    }
}