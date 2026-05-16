/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/16/2026 at 9:50 AM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Message consumed by {@link MpesaResultListener} from {@code mpesa.results.queue}.
 *
 * <p>Published by the Provisioning Service after attempting to post a payment
 * to the UA Service. Carries the outcome — success with receipt or failure with reason.
 *
 * <p>The message contract is shared between the Provisioning Service (publisher)
 * and the M-Pesa Gateway (consumer). Both sides must use the same field names
 * for Jackson serialisation to work correctly.
 *
 * <p><strong>Validation:</strong> invariants are enforced via a compact constructor.
 * Bean Validation is not triggered at construction time for records deserialized
 * by Jackson outside the Spring MVC lifecycle (Convention §6.1).
 *
 * @param correlationId    end-to-end trace identifier
 * @param paymentReference Safaricom transaction reference — matches {@code transId}
 *                         on {@link com.mycompany.api.mpesa.document.MpesaEvent}
 * @param success          {@code true} if the payment was posted successfully
 * @param billingReceipt   receipt number from the UA Service — non-null on success
 * @param failureReason    human-readable failure description — non-null on failure
 *
 * @author Oualid Gharach
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProvisioningResultMessage(
        UUID correlationId,
        String paymentReference,
        boolean success,
        String billingReceipt,
        String failureReason
) {
    public ProvisioningResultMessage {
        if (correlationId == null) {
            throw new IllegalArgumentException("correlationId is required");
        }
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new IllegalArgumentException("paymentReference is required");
        }
        if (success && (billingReceipt == null || billingReceipt.isBlank())) {
            throw new IllegalArgumentException("billingReceipt is required on success");
        }
        if (!success && (failureReason == null || failureReason.isBlank())) {
            throw new IllegalArgumentException("failureReason is required on failure");
        }
    }
}