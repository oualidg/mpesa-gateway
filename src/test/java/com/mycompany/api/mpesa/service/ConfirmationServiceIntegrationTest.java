/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/14/2026 at 9:17 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.service;

import com.mycompany.api.mpesa.client.UaValidationClient;
import com.mycompany.api.mpesa.document.MpesaEvent;
import com.mycompany.api.mpesa.document.OutboxEntry;
import com.mycompany.api.mpesa.dto.CallbackRequest;
import com.mycompany.api.mpesa.enums.MpesaEventState;
import com.mycompany.api.mpesa.repository.MpesaEventRepository;
import com.mycompany.api.mpesa.repository.OutboxEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ConfirmationService}.
 *
 * <p>Verifies end-to-end confirmation ingest behaviour against a real MongoDB
 * instance — atomic transaction semantics, idempotency, and state transitions.
 *
 * @author Oualid Gharach
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@EnableAutoConfiguration(exclude = {
        io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration.class,
        io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerMetricsAutoConfiguration.class
})
class ConfirmationServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ConfirmationService confirmationService;

    @Autowired
    private MpesaEventRepository mpesaEventRepository;

    @Autowired
    private OutboxEntryRepository outboxEntryRepository;

    @BeforeEach
    void setUp() {
        mpesaEventRepository.deleteAll();
        outboxEntryRepository.deleteAll();
    }

    // =========================================================================
    // Happy Path
    // =========================================================================

    @Test
    @DisplayName("Persists MpesaEvent as RECEIVED and creates OutboxEntry for valid request")
    void shouldPersistReceivedEventAndOutboxEntryForValidRequest() {
        confirmationService.ingest(validRequest("NLJ7RT61SV"));

        List<MpesaEvent> events = mpesaEventRepository.findAll();
        List<OutboxEntry> outboxEntries = outboxEntryRepository.findAll();

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getState()).isEqualTo(MpesaEventState.RECEIVED);
        assertThat(events.getFirst().getTransId()).isEqualTo("NLJ7RT61SV");
        assertThat(events.getFirst().getCreatedAt()).isNotNull();

        assertThat(outboxEntries).hasSize(1);
        assertThat(outboxEntries.getFirst().isSent()).isFalse();
        assertThat(outboxEntries.getFirst().getEventId()).isEqualTo(events.getFirst().getId());
    }

    // =========================================================================
    // Validation Failure — SUSPENDED
    // =========================================================================

    @Test
    @DisplayName("Persists MpesaEvent as SUSPENDED with no OutboxEntry for invalid shortcode")
    void shouldPersistSuspendedEventWithNoOutboxEntryForInvalidShortcode() {
        confirmationService.ingest(requestWithShortCode("WRONG"));

        List<MpesaEvent> events = mpesaEventRepository.findAll();
        List<OutboxEntry> outboxEntries = outboxEntryRepository.findAll();

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getState()).isEqualTo(MpesaEventState.SUSPENDED);
        assertThat(events.getFirst().getFailureReason()).isNotBlank();

        assertThat(outboxEntries).isEmpty();
    }

    @Test
    @DisplayName("Persists MpesaEvent as SUSPENDED with no OutboxEntry for invalid bill reference")
    void shouldPersistSuspendedEventWithNoOutboxEntryForInvalidBillRef() {
        confirmationService.ingest(requestWithBillRef("INVALID"));

        List<MpesaEvent> events = mpesaEventRepository.findAll();
        List<OutboxEntry> outboxEntries = outboxEntryRepository.findAll();

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getState()).isEqualTo(MpesaEventState.SUSPENDED);
        assertThat(events.getFirst().getFailureReason()).isNotBlank();

        assertThat(outboxEntries).isEmpty();
    }

    // =========================================================================
    // Idempotency
    // =========================================================================

    @Test
    @DisplayName("Ignores duplicate confirmation callback — only one event persisted")
    void shouldIgnoreDuplicateConfirmationCallback() {
        confirmationService.ingest(validRequest("NLJ7RT61SV"));
        confirmationService.ingest(validRequest("NLJ7RT61SV"));

        assertThat(mpesaEventRepository.findAll()).hasSize(1);
        assertThat(outboxEntryRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Processes two callbacks with different transIds independently")
    void shouldProcessTwoCallbacksWithDifferentTransIdsIndependently() {
        confirmationService.ingest(validRequest("NLJ7RT61SV"));
        confirmationService.ingest(validRequest("ABC1234567"));

        assertThat(mpesaEventRepository.findAll()).hasSize(2);
        assertThat(outboxEntryRepository.findAll()).hasSize(2);
    }

    // =========================================================================
    // State Invariants
    // =========================================================================

    @Test
    @DisplayName("RECEIVED event has no billingReceipt or failureReason")
    void shouldHaveNoReceiptOrFailureReasonForReceivedEvent() {
        confirmationService.ingest(validRequest("NLJ7RT61SV"));

        MpesaEvent event = mpesaEventRepository.findAll().getFirst();

        assertThat(event.getBillingReceipt()).isNull();
        assertThat(event.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("SUSPENDED event has failureReason and no billingReceipt")
    void shouldHaveFailureReasonAndNoReceiptForSuspendedEvent() {
        confirmationService.ingest(requestWithBillRef("INVALID"));

        MpesaEvent event = mpesaEventRepository.findAll().getFirst();

        assertThat(event.getFailureReason()).isNotBlank();
        assertThat(event.getBillingReceipt()).isNull();
    }

    // =========================================================================
    // Request builders
    // =========================================================================

    private CallbackRequest validRequest(String transId) {
        return new CallbackRequest(
                "Pay Bill", transId, "20240315123045",
                new BigDecimal("1500.00"), "600123", "1234567897",
                null, null, null, "254712345678", "John", null, "Doe"
        );
    }

    private CallbackRequest requestWithShortCode(String shortCode) {
        return new CallbackRequest(
                "Pay Bill", "NLJ7RT61SV", "20240315123045",
                new BigDecimal("1500.00"), shortCode, "1234567897",
                null, null, null, "254712345678", "John", null, "Doe"
        );
    }

    private CallbackRequest requestWithBillRef(String billRef) {
        return new CallbackRequest(
                "Pay Bill", "NLJ7RT61SV", "20240315123045",
                new BigDecimal("1500.00"), "600123", billRef,
                null, null, null, "254712345678", "John", null, "Doe"
        );
    }
}