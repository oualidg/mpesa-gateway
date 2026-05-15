/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/15/2026 at 9:23 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.messaging;

import com.mycompany.api.mpesa.document.MpesaEvent;
import com.mycompany.api.mpesa.document.OutboxEntry;
import com.mycompany.api.mpesa.enums.MpesaEventState;
import com.mycompany.api.mpesa.enums.OutboxStatus;
import com.mycompany.api.mpesa.repository.MpesaEventRepository;
import com.mycompany.api.mpesa.repository.OutboxEntryRepository;
import com.mycompany.api.mpesa.BaseIntegrationTest;
import com.mycompany.api.mpesa.util.BillRefNormaliser.ReferenceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Integration tests for {@link OutboxProcessor}.
 *
 * <p>Verifies MongoDB lifecycle transitions — claim, mark sent, reset to pending,
 * mark failed, and stale lease recovery. Uses real MongoDB via Testcontainers
 * and mocked {@link RabbitTemplate} to isolate broker interactions.
 *
 * @author Oualid Gharach
 */
class OutboxProcessorIntegrationTest extends BaseIntegrationTest {

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OutboxProcessor outboxProcessor;

    @Autowired
    private MpesaEventRepository mpesaEventRepository;

    @Autowired
    private OutboxEntryRepository outboxEntryRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        mpesaEventRepository.deleteAll();
        outboxEntryRepository.deleteAll();
    }

    // =========================================================================
    // Happy Path — PENDING → SENT
    // =========================================================================

    @Test
    @DisplayName("Transitions outbox entry from PENDING to SENT after successful publish")
    void shouldTransitionEntryFromPendingToSentAfterSuccessfulPublish() {
        MpesaEvent event = persistEvent();
        persistOutboxEntry(event);

        outboxProcessor.process();

        OutboxEntry entry = outboxEntryRepository.findAll().getFirst();
        assertThat(entry.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(entry.getPublishedAt()).isNotNull();
        assertThat(entry.getClaimedAt()).isNull();
    }

    @Test
    @DisplayName("Publishes provisioning message to RabbitMQ for PENDING entry")
    void shouldPublishProvisioningMessageForPendingEntry() {
        MpesaEvent event = persistEvent();
        persistOutboxEntry(event);

        outboxProcessor.process();

        verify(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    // =========================================================================
    // Publish Failure — PROCESSING → PENDING
    // =========================================================================

    @Test
    @DisplayName("Resets entry to PENDING with lastError when publish fails")
    void shouldResetEntryToPendingWithLastErrorWhenPublishFails() {
        MpesaEvent event = persistEvent();
        persistOutboxEntry(event);

        doThrow(new RuntimeException("Broker unavailable"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

        outboxProcessor.process();

        OutboxEntry entry = outboxEntryRepository.findAll().getFirst();
        assertThat(entry.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(entry.getLastError()).contains("Broker unavailable");
        assertThat(entry.getAttemptCount()).isEqualTo(1);
        assertThat(entry.getClaimedAt()).isNull();
    }

    // =========================================================================
    // Missing Event — PROCESSING → FAILED
    // =========================================================================

    @Test
    @DisplayName("Marks entry as FAILED when referenced MpesaEvent is missing")
    void shouldMarkEntryAsFailedWhenReferencedEventIsMissing() {
        OutboxEntry entry = OutboxEntry.forEvent(new org.bson.types.ObjectId());
        outboxEntryRepository.save(entry);

        outboxProcessor.process();

        OutboxEntry updated = outboxEntryRepository.findAll().getFirst();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(updated.getLastError()).isNotBlank();
        assertThat(updated.getClaimedAt()).isNull();
    }

    // =========================================================================
    // Stale Lease Recovery — PROCESSING → PENDING
    // =========================================================================

    @Test
    @DisplayName("Resets stale PROCESSING entry back to PENDING after lease timeout")
    void shouldResetStaleProcessingEntryBackToPending() {
        MpesaEvent event = persistEvent();
        OutboxEntry entry = persistOutboxEntry(event);

        // Force entry into PROCESSING with a stale claimedAt
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(entry.getId())),
                new Update()
                        .set("status", OutboxStatus.PROCESSING)
                        .set("claimedAt", Instant.now().minusSeconds(120)),
                OutboxEntry.class);

        outboxProcessor.process();

        OutboxEntry updated = outboxEntryRepository.findAll().getFirst();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.SENT);
        // Stale lease recovery does not increment attemptCount — it is a crash
        // recovery mechanism, not a business failure. Only publish failures increment attemptCount.
        assertThat(updated.getAttemptCount()).isZero();
    }

    @Test
    @DisplayName("Does not reset PROCESSING entry that is within lease timeout")
    void shouldNotResetProcessingEntryWithinLeaseTimeout() {
        MpesaEvent event = persistEvent();
        OutboxEntry entry = persistOutboxEntry(event);

        // Force entry into PROCESSING with a fresh claimedAt — within lease timeout
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(entry.getId())),
                new Update()
                        .set("status", OutboxStatus.PROCESSING)
                        .set("claimedAt", Instant.now()),
                OutboxEntry.class);

        outboxProcessor.process();

        OutboxEntry updated = outboxEntryRepository.findAll().getFirst();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(updated.getClaimedAt()).isNotNull();
    }

    // =========================================================================
    // No entries — no publish
    // =========================================================================

    @Test
    @DisplayName("Does not publish when no PENDING entries exist")
    void shouldNotPublishWhenNoPendingEntriesExist() {
        outboxProcessor.process();

        verify(rabbitTemplate, org.mockito.Mockito.never())
                .convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private MpesaEvent persistEvent() {
        MpesaEvent event = new MpesaEvent();
        event.setTransId("NLJ7RT61SV");
        event.setAmount(new BigDecimal("1500.00"));
        event.setBusinessShortCode("600123");
        event.setBillRefNumber("1234567897");
        event.setResolvedReferenceType(ReferenceType.ACCOUNT);
        event.setCorrelationId(UUID.randomUUID());
        event.setState(MpesaEventState.RECEIVED);
        event.setCreatedAt(Instant.now());
        return mpesaEventRepository.save(event);
    }

    private OutboxEntry persistOutboxEntry(MpesaEvent event) {
        OutboxEntry entry = OutboxEntry.forEvent(event.getId());
        return outboxEntryRepository.save(entry);
    }
}