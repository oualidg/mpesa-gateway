/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/14/2026 at 9:01 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.service;

import com.mycompany.api.mpesa.config.ValidationFallbackProperties;
import com.mycompany.api.mpesa.dto.ValidationResponse;
import com.mycompany.api.mpesa.enums.MpesaEventState;
import com.mycompany.api.mpesa.enums.ValidationResultCode;
import com.mycompany.api.mpesa.repository.MpesaEventRepository;
import com.mycompany.api.mpesa.util.BillRefNormaliser;
import com.mycompany.api.mpesa.util.BillRefNormaliser.BillRefStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ValidationFallbackService}.
 *
 * <p>Verifies ADR-006 fallback behaviour — recent event lookup, enabled/disabled
 * flag, and response mapping.
 *
 * @author Oualid Gharach
 */
@ExtendWith(MockitoExtension.class)
class ValidationFallbackServiceTest {

    @Mock
    private MpesaEventRepository mpesaEventRepository;

    @Mock
    private ValidationFallbackProperties fallbackProperties;

    @InjectMocks
    private ValidationFallbackService validationFallbackService;

    private final BillRefStrategy accountStrategy =
            BillRefNormaliser.normalise("1234567897").orElseThrow();

    // =========================================================================
    // Fallback Disabled
    // =========================================================================

    @Test
    @DisplayName("Returns other error when fallback is disabled")
    void shouldReturnOtherErrorWhenFallbackDisabled() {
        when(fallbackProperties.enabled()).thenReturn(false);

        ValidationResponse response = validationFallbackService.validate(accountStrategy);

        assertThat(response.resultCode()).isEqualTo(ValidationResultCode.OTHER_ERROR.code());
    }

    // =========================================================================
    // Fallback Enabled — Recent Event Found
    // =========================================================================

    @Test
    @DisplayName("Returns accepted when recent posted event exists for bill reference")
    void shouldReturnAcceptedWhenRecentPostedEventExists() {
        when(fallbackProperties.enabled()).thenReturn(true);
        when(fallbackProperties.window()).thenReturn(Duration.ofDays(90));
        when(mpesaEventRepository.existsByBillRefNumberAndStateAndCreatedAtAfter(
                eq("1234567897"), eq(MpesaEventState.POSTED), any(Instant.class)))
                .thenReturn(true);

        ValidationResponse response = validationFallbackService.validate(accountStrategy);

        assertThat(response.resultCode()).isEqualTo(ValidationResultCode.ACCEPTED.code());
    }

    // =========================================================================
    // Fallback Enabled — No Recent Event
    // =========================================================================

    @Test
    @DisplayName("Returns other error when no recent posted event exists for bill reference")
    void shouldReturnOtherErrorWhenNoRecentPostedEventExists() {
        when(fallbackProperties.enabled()).thenReturn(true);
        when(fallbackProperties.window()).thenReturn(Duration.ofDays(90));
        when(mpesaEventRepository.existsByBillRefNumberAndStateAndCreatedAtAfter(
                eq("1234567897"), eq(MpesaEventState.POSTED), any(Instant.class)))
                .thenReturn(false);

        ValidationResponse response = validationFallbackService.validate(accountStrategy);

        assertThat(response.resultCode()).isEqualTo(ValidationResultCode.OTHER_ERROR.code());
    }

    // =========================================================================
    // Repository Interaction
    // =========================================================================

    @Test
    @DisplayName("Queries repository with correct bill reference and POSTED state")
    void shouldQueryRepositoryWithCorrectBillRefAndPostedState() {
        when(fallbackProperties.enabled()).thenReturn(true);
        when(fallbackProperties.window()).thenReturn(Duration.ofDays(90));
        when(mpesaEventRepository.existsByBillRefNumberAndStateAndCreatedAtAfter(
                any(), any(), any())).thenReturn(true);

        validationFallbackService.validate(accountStrategy);

        verify(mpesaEventRepository).existsByBillRefNumberAndStateAndCreatedAtAfter(
                eq("1234567897"), eq(MpesaEventState.POSTED), any(Instant.class));
    }
}