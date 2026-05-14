/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 8:24 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.service;

import com.mycompany.api.mpesa.document.MpesaEvent;
import com.mycompany.api.mpesa.document.OutboxEntry;
import com.mycompany.api.mpesa.enums.MpesaEventState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Transactional helper for atomic persistence of {@link MpesaEvent} and
 * {@link OutboxEntry} during confirmation ingest.
 *
 * <p>Extracted from {@link ConfirmationService} into a separate {@code @Component}
 * to ensure Spring AOP proxy interception works correctly for {@code @Transactional}.
 * Self-invocation within the same bean bypasses the proxy and silently disables
 * transaction management (Convention §9.5).
 *
 * <p>Owns all MongoDB write operations for the confirmation ingest path —
 * both the atomic RECEIVED + outbox persist and the non-transactional SUSPENDED
 * persist are centralised here, keeping {@link ConfirmationService} focused on
 * orchestration and validation only.
 *
 * @author Oualid Gharach
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfirmationTransactionHelper {

    private final MongoTemplate mongoTemplate;

    /**
     * Persists a {@link MpesaEvent} in {@code RECEIVED} state and its corresponding
     * {@link OutboxEntry} atomically in a single MongoDB transaction.
     *
     * <p>The {@link OutboxEntry} references the persisted {@link MpesaEvent} by ID
     * and stores publication intent only — no business data is duplicated.
     *
     * <p>On transaction failure, the exception propagates to {@link ConfirmationService}
     * which allows it to reach the controller, returning HTTP 500 so Safaricom retries.
     *
     * @param event the validated event to persist
     */
    @Transactional
    public void persistReceivedWithOutbox(MpesaEvent event) {
        Instant now = Instant.now();
        event.setState(MpesaEventState.RECEIVED);
        event.setCreatedAt(now);

        MpesaEvent saved = mongoTemplate.insert(event);
        mongoTemplate.insert(OutboxEntry.forEvent(saved.getId()));
    }

    /**
     * Persists a {@link MpesaEvent} in {@code SUSPENDED} state with the given failure reason.
     *
     * <p>No outbox entry is created — suspended events are not forwarded for provisioning.
     * Duplicate key exceptions are handled silently — the event was already persisted
     * by a previous or concurrent callback.
     *
     * <p>This method is intentionally non-transactional — it is a single document
     * write with no dual-write concern.
     *
     * @param event         the event to suspend
     * @param failureReason the JSON-formatted failure reason
     */
    public void persistSuspended(MpesaEvent event, String failureReason) {
        event.setState(MpesaEventState.SUSPENDED);
        event.setFailureReason(failureReason);
        event.setCreatedAt(Instant.now());

        try {
            mongoTemplate.insert(event);
        } catch (DuplicateKeyException e) {
            log.info("Duplicate suspended event — ignoring.");
        } catch (Exception e) {
            log.error("CRITICAL — failed to persist suspended event. reason={}", e.getMessage(), e);
        }
    }
}