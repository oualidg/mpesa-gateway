/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/15/2026 at 1:34 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.messaging;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Message published by the outbox processor to {@code provisioning.queue} for
 * downstream payment execution by the Provisioning Service.
 *
 * <p>Contains all data required to post a payment to the Utility Account (UA) Service.
 * Exactly one of {@code accountNumber} or {@code customerNumber} must be populated —
 * never both, never neither.
 *
 * <p>{@code replyToRoutingKey} defines where the {@link ProvisioningResultMessage}
 * is published after processing. The Provisioning Service publishes to the payments
 * exchange using this routing key — the exchange handles queue resolution.
 *
 * <p>{@code paymentReference} is used as the idempotency key when calling the UA Service,
 * combined with {@code providerCode} to prevent duplicate payment posting under
 * at-least-once delivery.
 *
 * <p><strong>Validation:</strong> invariants are enforced via a compact constructor.
 * Bean Validation is not triggered at construction time for records deserialized by
 * Jackson outside the Spring MVC lifecycle — the compact constructor is the only
 * guarantee that fires unconditionally regardless of caller (Convention §6.1).
 *
 * @param correlationId      end-to-end trace identifier
 * @param providerCode       payment provider identifier (e.g. {@code MPESA})
 * @param paymentReference   Safaricom transaction reference — idempotency key
 * @param amount             payment amount
 * @param accountNumber      target account number — null if customerNumber is set
 * @param customerNumber     target customer number — null if accountNumber is set
 * @param replyToRoutingKey  routing key used when publishing the result to the payments exchange
 *
 * @author Oualid Gharach
 */
public record PaymentProvisioningMessage(
        UUID correlationId,
        String providerCode,
        String paymentReference,
        BigDecimal amount,
        String accountNumber,
        String customerNumber,
        String replyToRoutingKey
) {
    public PaymentProvisioningMessage {
        if (correlationId == null) {
            throw new IllegalArgumentException("correlationId is required");
        }
        if (providerCode == null || providerCode.isBlank()) {
            throw new IllegalArgumentException("providerCode is required");
        }
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new IllegalArgumentException("paymentReference is required");
        }
        if (replyToRoutingKey == null || replyToRoutingKey.isBlank()) {
            throw new IllegalArgumentException("replyToRoutingKey is required");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }

        boolean hasAccount = accountNumber != null && !accountNumber.isBlank();
        boolean hasCustomer = customerNumber != null && !customerNumber.isBlank();

        if (hasAccount == hasCustomer) {
            throw new IllegalArgumentException(
                    "Exactly one of accountNumber or customerNumber must be populated");
        }
    }
}