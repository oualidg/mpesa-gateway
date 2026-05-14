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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.api.mpesa.document.MpesaEvent;
import com.mycompany.api.mpesa.dto.CallbackRequest;
import com.mycompany.api.mpesa.mapper.MpesaEventMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ConfirmationService}.
 *
 * <p>Verifies orchestration behaviour — validation, persistence delegation,
 * and duplicate handling. Dependencies are mocked; MongoDB interaction is
 * tested in integration tests.
 *
 * @author Oualid Gharach
 */
@ExtendWith(MockitoExtension.class)
class ConfirmationServiceTest {

    @Mock
    private MpesaEventMapper mapper;

    @Mock
    private Validator validator;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ConfirmationTransactionHelper transactionHelper;

    @InjectMocks
    private ConfirmationService confirmationService;

    private CallbackRequest validRequest;
    private MpesaEvent mappedEvent;

    @BeforeEach
    void setUp() {
        validRequest = new CallbackRequest(
                "Pay Bill",
                "NLJ7RT61SV",
                "20240315123045",
                new BigDecimal("1500.00"),
                "600123",
                "1234567897",
                null,
                null,
                null,
                "254712345678",
                "John",
                null,
                "Doe"
        );

        mappedEvent = new MpesaEvent();
        mappedEvent.setTransId("NLJ7RT61SV");
        mappedEvent.setAmount(new BigDecimal("1500.00"));
        mappedEvent.setBusinessShortCode("600123");
        mappedEvent.setBillRefNumber("1234567897");
    }

    // =========================================================================
    // Happy Path
    // =========================================================================

    @Test
    @DisplayName("Persists event as RECEIVED with outbox when request is valid")
    void shouldPersistReceivedWithOutboxWhenRequestIsValid() {
        when(mapper.toMpesaEvent(validRequest)).thenReturn(mappedEvent);
        when(validator.validate(validRequest)).thenReturn(Set.of());

        confirmationService.ingest(validRequest);

        verify(transactionHelper).persistReceivedWithOutbox(mappedEvent);
        verify(transactionHelper, never()).persistSuspended(any(), any());
    }

    // =========================================================================
    // Validation Failure
    // =========================================================================

    @Test
    @DisplayName("Persists event as SUSPENDED when request fails validation")
    void shouldPersistSuspendedWhenValidationFails() throws Exception {
        @SuppressWarnings("unchecked")
        ConstraintViolation<CallbackRequest> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        when(violation.getMessage()).thenReturn("TransID is required");
        when(mapper.toMpesaEvent(validRequest)).thenReturn(mappedEvent);
        when(validator.validate(validRequest)).thenReturn(Set.of(violation));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"transId\":\"TransID is required\"}");

        confirmationService.ingest(validRequest);

        verify(transactionHelper).persistSuspended(eq(mappedEvent), any(String.class));
        verify(transactionHelper, never()).persistReceivedWithOutbox(any());
    }

    // =========================================================================
    // Duplicate Handling
    // =========================================================================

    @Test
    @DisplayName("Ignores duplicate confirmation callback silently")
    void shouldIgnoreDuplicateConfirmationCallback() {
        when(mapper.toMpesaEvent(validRequest)).thenReturn(mappedEvent);
        when(validator.validate(validRequest)).thenReturn(Set.of());
        doThrow(DuplicateKeyException.class)
                .when(transactionHelper).persistReceivedWithOutbox(mappedEvent);

        confirmationService.ingest(validRequest);

        verify(transactionHelper).persistReceivedWithOutbox(mappedEvent);
        verify(transactionHelper, never()).persistSuspended(any(), any());
    }

    // =========================================================================
    // MongoDB Failure
    // =========================================================================

    @Test
    @DisplayName("Propagates exception to controller when MongoDB transaction fails")
    void shouldPropagateExceptionWhenMongoTransactionFails() {
        when(mapper.toMpesaEvent(validRequest)).thenReturn(mappedEvent);
        when(validator.validate(validRequest)).thenReturn(Set.of());
        doThrow(new RuntimeException("MongoDB failure"))
                .when(transactionHelper).persistReceivedWithOutbox(mappedEvent);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> confirmationService.ingest(validRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MongoDB failure");
    }
}