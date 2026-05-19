/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/16/2026 at 9:53 AM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.service;

import com.mycompany.api.mpesa.document.MpesaEvent;
import com.mycompany.api.mpesa.enums.MpesaEventState;
import com.mycompany.api.mpesa.messaging.ProvisioningResultMessage;
import com.mycompany.api.mpesa.metrics.EventStateMetrics;
import com.mycompany.api.mpesa.repository.MpesaEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Processes provisioning results received from the Provisioning Service via RabbitMQ.
 *
 * <p>Stage 3 of the confirmation pipeline — transitions a {@link MpesaEvent} from
 * {@code RECEIVED} to either {@code POSTED} or {@code SUSPENDED} based on the
 * provisioning outcome.
 *
 * <p>Invariants enforced:
 * <ul>
 *   <li>{@code POSTED} must have {@code billingReceipt} and {@code postedAt}</li>
 *   <li>{@code SUSPENDED} must have {@code failureReason}</li>
 * </ul>
 *
 * <p>If the referenced {@link MpesaEvent} is not found, an {@link IllegalStateException}
 * is thrown — the listener rejects the message to {@code results.dlq} for operator
 * investigation. Missing events indicate inconsistent state and should not be silently ignored.
 *
 * <p>Any exception thrown by this service causes {@link com.mycompany.api.mpesa.messaging.MpesaResultListener}
 * to reject the message to {@code results.dlq} for operator review.
 *
 * <p>Terminal state transitions are recorded via {@link EventStateMetrics} for
 * Prometheus scraping.
 *
 * @author Oualid Gharach
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultProcessingService {

    private final MpesaEventRepository mpesaEventRepository;
    private final EventStateMetrics eventStateMetrics;

    /**
     * Processes a provisioning result and transitions the corresponding
     * {@link MpesaEvent} to its terminal state.
     *
     * @param result the provisioning result from the Provisioning Service
     * @throws IllegalStateException if the referenced event is not found
     */
    public void process(ProvisioningResultMessage result) {
        MpesaEvent event = mpesaEventRepository.findByTransId(result.paymentReference())
                .orElseThrow(() -> {
                    log.error("MpesaEvent not found for paymentReference={} — cannot apply result.",
                            result.paymentReference());
                    return new IllegalStateException(
                            "MpesaEvent not found for paymentReference: " + result.paymentReference());
                });

        // Only guard against POSTED — payment was successfully provisioned and receipt issued.
        // SUSPENDED events can be recovered by ops replaying the outbox without manual state resets.
        if (event.getState() == MpesaEventState.POSTED) {
            log.warn("MpesaEvent is already POSTED — result ignored. transId={}", event.getTransId());
            return;
        }

        if (result.success()) {
            applyPosted(event, result.billingReceipt());
        } else {
            applySuspended(event, result.failureReason());
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Transitions the event to {@code POSTED} with the billing receipt.
     *
     * @param event          the event to transition
     * @param billingReceipt the receipt from the UA Service
     */
    private void applyPosted(MpesaEvent event, String billingReceipt) {
        Instant now = Instant.now();
        event.setState(MpesaEventState.POSTED);
        event.setBillingReceipt(billingReceipt);
        event.setFailureReason(null); // clear previous failure reason on successful recovery
        event.setPostedAt(now);
        event.setUpdatedAt(now);
        mpesaEventRepository.save(event);
        eventStateMetrics.recordPosted();
        log.info("MpesaEvent transitioned to POSTED. transId={} billingReceipt={}",
                event.getTransId(), billingReceipt);
    }

    /**
     * Transitions the event to {@code SUSPENDED} with the failure reason.
     *
     * @param event         the event to transition
     * @param failureReason the reason for failure
     */
    private void applySuspended(MpesaEvent event, String failureReason) {
        event.setState(MpesaEventState.SUSPENDED);
        event.setFailureReason(failureReason);
        event.setUpdatedAt(Instant.now());
        mpesaEventRepository.save(event);
        eventStateMetrics.recordSuspended();
        log.warn("MpesaEvent transitioned to SUSPENDED. transId={} reason={}",
                event.getTransId(), failureReason);
    }
}