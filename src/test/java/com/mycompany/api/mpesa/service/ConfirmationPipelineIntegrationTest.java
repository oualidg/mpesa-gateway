/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/16/2026 at 8:55 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.service;

import com.mycompany.api.mpesa.BaseIntegrationTest;
import com.mycompany.api.mpesa.document.MpesaEvent;
import com.mycompany.api.mpesa.document.OutboxEntry;
import com.mycompany.api.mpesa.dto.CallbackRequest;
import com.mycompany.api.mpesa.enums.MpesaEventState;
import com.mycompany.api.mpesa.enums.OutboxStatus;
import com.mycompany.api.mpesa.messaging.OutboxProcessor;
import com.mycompany.api.mpesa.messaging.PaymentProvisioningMessage;
import com.mycompany.api.mpesa.messaging.ProvisioningResultMessage;
import com.mycompany.api.mpesa.repository.MpesaEventRepository;
import com.mycompany.api.mpesa.repository.OutboxEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * End-to-end integration tests for the confirmation pipeline.
 *
 * <p>Proves the full async pipeline against real MongoDB:
 * <ol>
 *   <li>Confirmation ingest → {@code MpesaEvent: RECEIVED} + {@code OutboxEntry: PENDING}</li>
 *   <li>Outbox processor → publishes {@link PaymentProvisioningMessage} to RabbitMQ (mocked)</li>
 *   <li>Result processing → {@code MpesaEvent: POSTED} or {@code SUSPENDED}</li>
 * </ol>
 *
 * <p>RabbitMQ is mocked — the provisioning message is captured and a
 * {@link ProvisioningResultMessage} is built from it to simulate the Provisioning
 * Service response without requiring a live broker.
 *
 * @author Oualid Gharach
 */
class ConfirmationPipelineIntegrationTest extends BaseIntegrationTest {

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ConfirmationService confirmationService;

    @Autowired
    private OutboxProcessor outboxProcessor;

    @Autowired
    private ResultProcessingService resultProcessingService;

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
    // Full pipeline — success
    // =========================================================================

    @Test
    @DisplayName("Full pipeline — ingest to POSTED via successful provisioning result")
    void shouldTransitionEventToPostedAfterSuccessfulProvisioningResult() {
        // Stage 1 — ingest
        confirmationService.ingest(validRequest());

        MpesaEvent event = mpesaEventRepository.findAll().getFirst();
        assertThat(event.getState()).isEqualTo(MpesaEventState.RECEIVED);

        // Stage 2 — outbox processor publishes
        outboxProcessor.process();

        List<OutboxEntry> entries = outboxEntryRepository.findAll();
        assertThat(entries.getFirst().getStatus()).isEqualTo(OutboxStatus.SENT);

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(any(String.class), any(String.class), captor.capture());
        PaymentProvisioningMessage published = (PaymentProvisioningMessage) captor.getValue();
        assertThat(published.paymentReference()).isEqualTo("NLJ7RT61SV");
        assertThat(published.accountNumber()).isEqualTo("1234567897");
        assertThat(published.correlationId()).isEqualTo(event.getCorrelationId());

        // Stage 3 — simulate success result from Provisioning Service
        var result = new ProvisioningResultMessage(
                published.correlationId(),
                published.paymentReference(),
                true,
                "receipt-abc123",
                null);

        resultProcessingService.process(result);

        MpesaEvent updated = mpesaEventRepository.findAll().getFirst();
        assertThat(updated.getState()).isEqualTo(MpesaEventState.POSTED);
        assertThat(updated.getBillingReceipt()).isEqualTo("receipt-abc123");
        assertThat(updated.getPostedAt()).isNotNull();
        assertThat(updated.getFailureReason()).isNull();
    }

    // =========================================================================
    // Full pipeline — failure
    // =========================================================================

    @Test
    @DisplayName("Full pipeline — ingest to SUSPENDED via failed provisioning result")
    void shouldTransitionEventToSuspendedAfterFailedProvisioningResult() {
        // Stage 1 — ingest
        confirmationService.ingest(validRequest());

        // Stage 2 — outbox processor publishes
        outboxProcessor.process();

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(any(String.class), any(String.class), captor.capture());
        PaymentProvisioningMessage published = (PaymentProvisioningMessage) captor.getValue();

        // Stage 3 — simulate failure result from Provisioning Service
        var result = new ProvisioningResultMessage(
                published.correlationId(),
                published.paymentReference(),
                false,
                null,
                "Account not found");

        resultProcessingService.process(result);

        MpesaEvent updated = mpesaEventRepository.findAll().getFirst();
        assertThat(updated.getState()).isEqualTo(MpesaEventState.SUSPENDED);
        assertThat(updated.getFailureReason()).isEqualTo("Account not found");
        assertThat(updated.getBillingReceipt()).isNull();
    }

    // =========================================================================
    // Full pipeline — ops recovery
    // =========================================================================

    @Test
    @DisplayName("Full pipeline — SUSPENDED event recovered to POSTED after outbox replay")
    void shouldRecoverSuspendedEventToPostedAfterOutboxReplay() {
        // Stage 1 — ingest
        confirmationService.ingest(validRequest());

        // Stage 2 — outbox publishes, failure result arrives
        outboxProcessor.process();

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(any(String.class), any(String.class), captor.capture());
        PaymentProvisioningMessage published = (PaymentProvisioningMessage) captor.getValue();

        resultProcessingService.process(new ProvisioningResultMessage(
                published.correlationId(), published.paymentReference(),
                false, null, "Transient failure"));

        MpesaEvent suspended = mpesaEventRepository.findAll().getFirst();
        assertThat(suspended.getState()).isEqualTo(MpesaEventState.SUSPENDED);

        // Stage 3 — ops resets OutboxEntry to PENDING (simulated)
        OutboxEntry entry = outboxEntryRepository.findAll().getFirst();
        entry.setStatus(OutboxStatus.PENDING);
        entry.setClaimedAt(null);
        outboxEntryRepository.save(entry);

        // Stage 4 — outbox replays, success result arrives
        outboxProcessor.process();

        resultProcessingService.process(new ProvisioningResultMessage(
                published.correlationId(), published.paymentReference(),
                true, "recovery-receipt", null));

        MpesaEvent recovered = mpesaEventRepository.findAll().getFirst();
        assertThat(recovered.getState()).isEqualTo(MpesaEventState.POSTED);
        assertThat(recovered.getBillingReceipt()).isEqualTo("recovery-receipt");
        assertThat(recovered.getFailureReason()).isNull();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private CallbackRequest validRequest() {
        return new CallbackRequest(
                "Pay Bill", "NLJ7RT61SV", "20240315123045",
                new BigDecimal("1500.00"), "600123", "1234567897",
                null, null, null, "254712345678", "John", null, "Doe"
        );
    }
}