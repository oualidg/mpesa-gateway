/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 1:13 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.document;

import com.mycompany.api.mpesa.enums.MpesaEventState;
import com.mycompany.api.mpesa.util.BillRefNormaliser.ReferenceType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * MongoDB document representing a Safaricom M-Pesa C2B payment event.
 *
 * <p>Created on confirmation callback ingest and updated throughout its lifecycle.
 * Acts as the source of truth for all M-Pesa payment processing within the Gateway.
 *
 * <p>{@code correlationId} is set at ingest from the HTTP request MDC context and
 * propagated through the entire processing pipeline — outbox publish, provisioning,
 * and result handling — enabling end-to-end traceability using a single identifier.
 *
 * <p>{@code resolvedReferenceType} is set at ingest after successful bill reference
 * normalisation. The outbox processor reads it directly — no re-normalisation required.
 *
 * <p>State transitions:
 * <ul>
 *   <li>{@code RECEIVED} → {@code POSTED} on successful UA Service posting</li>
 *   <li>{@code RECEIVED} → {@code SUSPENDED} on validation failure or retry exhaustion</li>
 * </ul>
 *
 * <p>Invariants enforced by the service layer:
 * <ul>
 *   <li>{@code POSTED} must have {@code billingReceipt} and {@code postedAt}</li>
 *   <li>{@code SUSPENDED} must have {@code failureReason}</li>
 *   <li>{@code RECEIVED} must have neither {@code billingReceipt} nor {@code failureReason}</li>
 * </ul>
 *
 * <p>The sparse unique index on {@code transId} serves as the idempotency control for
 * duplicate Safaricom callbacks. Null {@code transId} is allowed for malformed callbacks
 * that are persisted as {@code SUSPENDED}.
 *
 * <p>{@code @Data} is not used — Lombok-generated {@code equals}/{@code hashCode}
 * on all fields is inappropriate for MongoDB documents. {@code equals} and
 * {@code hashCode} are based on {@code id} only.
 *
 * @author Oualid Gharach
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "mpesa_events")
@CompoundIndex(name = "idx_state_created_at", def = "{'state': 1, 'createdAt': 1}")
public class MpesaEvent {

    @Id
    private ObjectId id;

    // =========================================================================
    // Payment processing fields
    // =========================================================================

    /** Safaricom unique transaction reference. Sparse unique index — idempotency key. Null allowed for malformed callbacks. */
    @Indexed(unique = true, sparse = true)
    private String transId;

    private BigDecimal amount;
    private String businessShortCode;

    /** Normalised bill reference stored as received. Reference type resolved at ingest and stored on {@code resolvedReferenceType}. */
    private String billRefNumber;

    // =========================================================================
    // Reconciliation fields — stored as-is. DO NOT add validation annotations
    // here. Missing or malformed values must never cause a payment to be suspended.
    // =========================================================================

    private String transactionType;

    /** Raw Safaricom timestamp string — stored as-is for reconciliation. Never parsed or forwarded. */
    private String transTime;

    private String invoiceNumber;
    private BigDecimal orgAccountBalance;
    private String thirdPartyTransId;
    private String msisdn;
    private String firstName;
    private String middleName;
    private String lastName;

    // =========================================================================
    // Lifecycle fields — set by the service layer, never by the mapper.
    // =========================================================================

    /** End-to-end trace identifier — set at ingest from HTTP request MDC context. Propagated through outbox and provisioning. */
    private UUID correlationId;

    /**
     * Resolved bill reference type — set at ingest after normalisation.
     * The outbox processor reads this directly to avoid re-normalising at publish time.
     * Null for SUSPENDED events where normalisation failed.
     */
    private ReferenceType resolvedReferenceType;

    private MpesaEventState state;
    private String billingReceipt;
    private String failureReason;

    private Instant createdAt;
    private Instant postedAt;
    private Instant updatedAt;

    // =========================================================================
    // equals / hashCode — based on id only
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MpesaEvent that = (MpesaEvent) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}