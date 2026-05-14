/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/14/2026 at 8:28 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.service;

import com.mycompany.api.mpesa.client.UaValidationClient;
import com.mycompany.api.mpesa.dto.CallbackRequest;
import com.mycompany.api.mpesa.dto.ValidationResponse;
import com.mycompany.api.mpesa.enums.ValidationResultCode;
import com.mycompany.api.mpesa.util.BillRefNormaliser.BillRefStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

/**
 * Unit tests for {@link ValidationService}.
 *
 * <p>Verifies orchestration behaviour — bill reference normalisation and
 * delegation to {@link UaValidationClient}. HTTP interaction is tested
 * separately via WireMock.
 *
 * @author Oualid Gharach
 */
@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {

    @Mock
    private UaValidationClient uaValidationClient;

    @InjectMocks
    private ValidationService validationService;

    private CallbackRequest validAccountRequest;
    private CallbackRequest validCustomerRequest;

    @BeforeEach
    void setUp() {
        validAccountRequest = new CallbackRequest(
                "Pay Bill",
                "NLJ7RT61SV",
                "20240315123045",
                new BigDecimal("1500.00"),
                "600123",
                "1234567897",  // valid 10-digit Luhn — account
                null, null, null,
                "254712345678",
                "John", null, "Doe"
        );

        validCustomerRequest = new CallbackRequest(
                "Pay Bill",
                "NLJ7RT61SV",
                "20240315123045",
                new BigDecimal("1500.00"),
                "600123",
                "12345674",    // valid 8-digit Luhn — customer
                null, null, null,
                "254712345678",
                "John", null, "Doe"
        );
    }

    // =========================================================================
    // Accepted
    // =========================================================================

    @Test
    @DisplayName("Returns accepted response when UA Service accepts account number")
    void shouldReturnAcceptedWhenUaServiceAcceptsAccountNumber() {
        when(uaValidationClient.validate(any(BillRefStrategy.class)))
                .thenReturn(ValidationResponse.accepted());

        ValidationResponse response = validationService.validate(validAccountRequest);

        assertThat(response.resultCode()).isEqualTo(ValidationResultCode.ACCEPTED.code());
        verify(uaValidationClient).validate(any(BillRefStrategy.class));
    }

    @Test
    @DisplayName("Returns accepted response when UA Service accepts customer number")
    void shouldReturnAcceptedWhenUaServiceAcceptsCustomerNumber() {
        when(uaValidationClient.validate(any(BillRefStrategy.class)))
                .thenReturn(ValidationResponse.accepted());

        ValidationResponse response = validationService.validate(validCustomerRequest);

        assertThat(response.resultCode()).isEqualTo(ValidationResultCode.ACCEPTED.code());
        verify(uaValidationClient).validate(any(BillRefStrategy.class));
    }

    // =========================================================================
    // Rejected
    // =========================================================================

    @Test
    @DisplayName("Returns rejection response when UA Service rejects account")
    void shouldReturnRejectionWhenUaServiceRejectsAccount() {
        when(uaValidationClient.validate(any(BillRefStrategy.class)))
                .thenReturn(ValidationResponse.rejected(
                        ValidationResultCode.INVALID_ACCOUNT.code(),
                        ValidationResultCode.INVALID_ACCOUNT.description()));

        ValidationResponse response = validationService.validate(validAccountRequest);

        assertThat(response.resultCode()).isEqualTo(ValidationResultCode.INVALID_ACCOUNT.code());
    }

    // =========================================================================
    // Strategy Routing
    // =========================================================================

    @Test
    @DisplayName("Passes account strategy to UA client for 10-digit bill reference")
    void shouldPassAccountStrategyForTenDigitBillRef() {
        when(uaValidationClient.validate(any(BillRefStrategy.class)))
                .thenReturn(ValidationResponse.accepted());

        validationService.validate(validAccountRequest);

        verify(uaValidationClient).validate(argThat(strategy ->
                strategy.path().contains("accounts") &&
                        strategy.reference().equals("1234567897")));
    }

    @Test
    @DisplayName("Passes customer strategy to UA client for 8-digit bill reference")
    void shouldPassCustomerStrategyForEightDigitBillRef() {
        when(uaValidationClient.validate(any(BillRefStrategy.class)))
                .thenReturn(ValidationResponse.accepted());

        validationService.validate(validCustomerRequest);

        verify(uaValidationClient).validate(argThat(strategy ->
                strategy.path().contains("customers") &&
                        strategy.reference().equals("12345674")));
    }
}