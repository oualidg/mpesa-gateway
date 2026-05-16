/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/15/2026 at 1:33 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.messaging;

import com.mycompany.api.mpesa.config.MessagingConfig.MessagingProperties;
import com.mycompany.api.mpesa.config.OutboxProperties;
import com.mycompany.api.mpesa.document.MpesaEvent;
import com.mycompany.api.mpesa.document.OutboxEntry;
import com.mycompany.api.mpesa.enums.OutboxStatus;
import com.mycompany.api.mpesa.util.BillRefNormaliser.ReferenceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.mycompany.api.mpesa.config.AppConfig.PROVIDER_CODE;

/**
 * Scheduled component that polls the outbox collection for pending entries and
 * publishes provisioning messages to RabbitMQ.
 *
 * <p>Runs every {@code app.outbox.poll-interval} (default 5 seconds). On each cycle:
 * <ol>
 *   <li>Resets stale {@code PROCESSING} entries back to {@code PENDING} — recovers from
 *       JVM crashes where the entry was claimed but publish never completed</li>
 *   <li>Atomically claims a batch of {@code PENDING} entries — sets status to
 *       {@code PROCESSING} with {@code claimedAt} timestamp</li>
 *   <li>For each claimed entry, loads the referenced {@link MpesaEvent} and builds
 *       a {@link PaymentProvisioningMessage}</li>
 *   <li>Publishes to RabbitMQ — on success sets {@code SENT}, on failure resets to
 *       {@code PENDING} with {@code lastError} recorded and {@code attemptCount} incremented</li>
 * </ol>
 *
 * <p>Status model:
 * <ul>
 *   <li>{@code PENDING} → {@code PROCESSING} — atomic claim</li>
 *   <li>{@code PROCESSING} → {@code SENT} — successful publish call</li>
 *   <li>{@code PROCESSING} → {@code PENDING} — publish failure or stale lease</li>
 *   <li>{@code PROCESSING} → {@code FAILED} — missing event (permanent)</li>
 * </ul>
 *
 * <p>MongoDB does not support atomic bulk claim — entries are claimed one at a time
 * using {@code findAndModify}. This is the correct pattern for safe concurrent claiming
 * and is acceptable at the expected scale (~5–8 tx/sec peak).
 *
 * @author Oualid Gharach
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxProcessor {

    private final MongoTemplate mongoTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxProperties outboxProperties;
    private final MessagingProperties messagingProperties;

    /**
     * Main poll cycle — resets stale leases then claims and processes pending entries.
     */
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval}")
    public void process() {
        resetStaleLeasedEntries();

        List<OutboxEntry> entries = claimBatch();
        if (entries.isEmpty()) {
            return;
        }

        log.debug("Outbox processor — claimed {} entries for processing.", entries.size());

        for (OutboxEntry entry : entries) {
            processEntry(entry);
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Resets stale {@code PROCESSING} entries back to {@code PENDING}.
     *
     * <p>An entry is considered stale if it has been in {@code PROCESSING} for longer
     * than the configured lease timeout — indicating the JVM crashed after claim but
     * before publish completed.
     *
     * <p>Safe for multiple instances — {@code updateMulti} is atomic per document in
     * MongoDB. Both instances running simultaneously will converge on the same result.
     */
    private void resetStaleLeasedEntries() {
        Instant leaseExpiry = Instant.now().minus(outboxProperties.leaseTimeout());

        Query staleQuery = new Query(
                Criteria.where("status").is(OutboxStatus.PROCESSING)
                        .and("claimedAt").lt(leaseExpiry));

        Update resetUpdate = new Update()
                .set("status", OutboxStatus.PENDING)
                .unset("claimedAt");

        var result = mongoTemplate.updateMulti(staleQuery, resetUpdate, OutboxEntry.class);

        if (result.getModifiedCount() > 0) {
            log.warn("Outbox processor — reset {} stale PROCESSING entries back to PENDING.",
                    result.getModifiedCount());
        }
    }

    /**
     * Atomically claims a batch of {@code PENDING} outbox entries.
     *
     * <p>Sets status to {@code PROCESSING} and records {@code claimedAt} timestamp.
     * The lease timeout protects against permanent lock if the JVM crashes.
     *
     * <p>Each entry is claimed individually via {@code findAndModify} — MongoDB has
     * no atomic bulk claim equivalent to PostgreSQL's {@code SELECT FOR UPDATE SKIP LOCKED}.
     * This results in N round trips per batch, which is acceptable at the expected scale.
     *
     * @return list of claimed outbox entries
     */
    private List<OutboxEntry> claimBatch() {
        List<OutboxEntry> claimed = new ArrayList<>();

        Query claimQuery = new Query(Criteria.where("status").is(OutboxStatus.PENDING))
                .with(Sort.by(Sort.Direction.ASC, "createdAt"));

        Update claimUpdate = new Update()
                .set("status", OutboxStatus.PROCESSING)
                .set("claimedAt", Instant.now());

        for (int i = 0; i < outboxProperties.batchSize(); i++) {
            OutboxEntry claimedEntry = mongoTemplate.findAndModify(
                    claimQuery,
                    claimUpdate,
                    FindAndModifyOptions.options().returnNew(true),
                    OutboxEntry.class);

            if (claimedEntry == null) {
                break;
            }

            claimed.add(claimedEntry);
        }

        return claimed;
    }

    /**
     * Processes a single outbox entry — loads the event, builds the message,
     * and publishes to RabbitMQ.
     *
     * @param entry the claimed outbox entry
     */
    private void processEntry(OutboxEntry entry) {
        MpesaEvent event = mongoTemplate.findById(entry.getEventId(), MpesaEvent.class);

        if (event == null) {
            log.error("Outbox entry references missing MpesaEvent — marking FAILED. " +
                    "entryId={} eventId={}", entry.getId(), entry.getEventId());
            markFailed(entry);
            return;
        }

        PaymentProvisioningMessage message = buildMessage(event);

        try {
            rabbitTemplate.convertAndSend(
                    messagingProperties.exchange(),
                    messagingProperties.provisioningRoutingKey(),
                    message);

            markSent(entry);
            log.info("Outbox entry published successfully. transId={} entryId={}",
                    event.getTransId(), entry.getId());

        } catch (Exception e) {
            log.error("Failed to publish outbox entry — resetting to PENDING. " +
                    "transId={} entryId={} reason={}", event.getTransId(), entry.getId(), e.getMessage());
            resetToPending(entry, e.getMessage());
        }
    }

    /**
     * Builds a {@link PaymentProvisioningMessage} from a {@link MpesaEvent}.
     *
     * <p>{@code resolvedReferenceType} is read directly from the event — set at
     * ingest time, no re-normalisation required.
     *
     * <p>{@code correlationId} is read from the event — propagated from the original
     * HTTP ingest request MDC context for end-to-end traceability.
     *
     * @param event the source event
     * @return the provisioning message
     */
    private PaymentProvisioningMessage buildMessage(MpesaEvent event) {
        var type = event.getResolvedReferenceType();
        return new PaymentProvisioningMessage(
                event.getCorrelationId(),
                PROVIDER_CODE,
                event.getTransId(),
                event.getAmount(),
                type == ReferenceType.ACCOUNT ? event.getBillRefNumber() : null,
                type == ReferenceType.CUSTOMER ? event.getBillRefNumber() : null,
                messagingProperties.mpesaResultRoutingKey()
        );
    }

    /**
     * Marks an outbox entry as {@code SENT} after successful publish call.
     *
     * <p>Guarded by {@code status = PROCESSING} to avoid stale updates — only
     * the instance that claimed this entry can transition it to {@code SENT}.
     *
     * @param entry the successfully published entry
     */
    private void markSent(OutboxEntry entry) {
        Query query = new Query(
                Criteria.where("_id").is(entry.getId())
                        .and("status").is(OutboxStatus.PROCESSING));
        Update update = new Update()
                .set("status", OutboxStatus.SENT)
                .set("publishedAt", Instant.now())
                .unset("claimedAt");
        mongoTemplate.updateFirst(query, update, OutboxEntry.class);
    }

    /**
     * Resets an outbox entry to {@code PENDING} after a publish failure.
     *
     * @param entry     the entry to reset
     * @param lastError the error message for operational debugging
     */
    private void resetToPending(OutboxEntry entry, String lastError) {
        Query query = new Query(Criteria.where("_id").is(entry.getId()));
        Update update = new Update()
                .set("status", OutboxStatus.PENDING)
                .unset("claimedAt")
                .inc("attemptCount", 1)
                .set("lastError", lastError);
        mongoTemplate.updateFirst(query, update, OutboxEntry.class);
    }

    /**
     * Marks an outbox entry as {@code FAILED} — permanent failure, will not retry.
     *
     * @param entry the entry to fail
     */
    private void markFailed(OutboxEntry entry) {
        Query query = new Query(Criteria.where("_id").is(entry.getId()));
        Update update = new Update()
                .set("status", OutboxStatus.FAILED)
                .set("lastError", "Referenced MpesaEvent not found")
                .unset("claimedAt");
        mongoTemplate.updateFirst(query, update, OutboxEntry.class);
    }
}