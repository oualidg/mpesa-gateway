/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 5:45 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.api.mpesa.document.MpesaEvent;
import com.mycompany.api.mpesa.dto.CallbackRequest;
import com.mycompany.api.mpesa.mapper.MpesaEventMapper;
import com.mycompany.api.mpesa.util.BillRefNormaliser;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles Safaricom C2B confirmation callbacks — Stage 1 of the confirmation pipeline.
 *
 * <p>Maps the inbound request to a {@link MpesaEvent}, validates critical fields,
 * and persists the event atomically with its outbox entry. Invalid events are
 * persisted as {@code SUSPENDED} rather than rejected — no callback is ever lost.
 *
 * <p>Sets {@code correlationId} from MDC at ingest time so the same identifier
 * propagates end-to-end through outbox publish, provisioning, and result handling.
 *
 * <p>Sets {@code resolvedReferenceType} after bill reference normalisation so the
 * outbox processor reads it directly without re-normalising at publish time.
 *
 * <p>All MongoDB writes are delegated to {@link ConfirmationTransactionHelper}.
 * {@code @Transactional} is not used here — see Convention §9.5.
 *
 * @author Oualid Gharach
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmationService {

    private final MpesaEventMapper mapper;
    private final Validator validator;
    private final ObjectMapper objectMapper;
    private final ConfirmationTransactionHelper transactionHelper;

    private static final String MDC_TRANS_ID = "transId";

    /**
     * Ingests a Safaricom C2B confirmation callback.
     *
     * <p>Returns normally in all cases — the controller always returns HTTP 200.
     * Throws only on MongoDB transaction failure, which causes the controller
     * to return HTTP 500 so Safaricom retries the callback.
     *
     * @param request the inbound Safaricom confirmation payload
     * @throws RuntimeException if the atomic MongoDB transaction fails
     */
    public void ingest(CallbackRequest request) {
        MpesaEvent event = mapper.toMpesaEvent(request);
        MDC.put(MDC_TRANS_ID, event.getTransId());

        try {
            log.info("Received confirmation callback. shortCode={} amount={} billRef={} transTime={}",
                    event.getBusinessShortCode(),
                    event.getAmount(),
                    event.getBillRefNumber(),
                    event.getTransTime());

            // Set correlationId from MDC at the first ingest boundary —
            // propagates end-to-end through outbox, provisioning, and result handling.
            String mdcCorrelationId = MDC.get("correlationId");
            event.setCorrelationId(mdcCorrelationId != null
                    ? UUID.fromString(mdcCorrelationId)
                    : UUID.randomUUID());

            Set<ConstraintViolation<CallbackRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                String reason = buildViolationReason(violations);
                log.warn("Confirmation failed validation — suspending. reason={}", reason);
                transactionHelper.persistSuspended(event, reason);
                return;
            }

            // Resolve and store the reference type at ingest — outbox processor
            // reads it directly without re-normalising at publish time.
            BillRefNormaliser.normalise(event.getBillRefNumber())
                    .ifPresent(strategy -> event.setResolvedReferenceType(strategy.type()));

            try {
                transactionHelper.persistReceivedWithOutbox(event);
                log.info("Confirmation ingested successfully.");
            } catch (DuplicateKeyException e) {
                log.info("Duplicate confirmation callback — ignoring.");
            }

        } finally {
            MDC.remove(MDC_TRANS_ID);
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Builds a JSON-formatted failure reason string from constraint violations.
     *
     * @param violations the set of constraint violations
     * @return JSON-formatted failure reason string
     */
    private String buildViolationReason(Set<ConstraintViolation<CallbackRequest>> violations) {
        Map<String, String> violationMap = violations.stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (a, b) -> a + "; " + b
                ));
        try {
            return objectMapper.writeValueAsString(violationMap);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise violation reasons to JSON — falling back to plain string.");
            return violationMap.entrySet().stream()
                    .map(entry -> entry.getKey() + " " + entry.getValue())
                    .collect(Collectors.joining(", "));
        }
    }
}