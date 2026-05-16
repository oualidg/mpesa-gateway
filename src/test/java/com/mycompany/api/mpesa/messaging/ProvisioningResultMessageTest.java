/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/16/2026 at 9:18 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ProvisioningResultMessage}.
 *
 * <p>Verifies compact constructor validation and Jackson deserialization behaviour —
 * including tolerance for unknown fields published by the Provisioning Service.
 *
 * @author Oualid Gharach
 */
class ProvisioningResultMessageTest {

    private static final UUID CORRELATION_ID = UUID.randomUUID();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // =========================================================================
    // Deserialization — unknown fields
    // =========================================================================

    @Test
    @DisplayName("Deserializes correctly when message contains unknown fields from Provisioning Service")
    void shouldDeserializeCorrectlyWithUnknownFields() throws Exception {
        String json = """
                {
                    "correlationId": "%s",
                    "providerCode": "MPESA",
                    "paymentReference": "NLJ7RT61SV",
                    "success": true,
                    "billingReceipt": "receipt-123",
                    "failureReason": null
                }
                """.formatted(CORRELATION_ID);

        var result = objectMapper.readValue(json, ProvisioningResultMessage.class);

        assertThat(result.paymentReference()).isEqualTo("NLJ7RT61SV");
        assertThat(result.billingReceipt()).isEqualTo("receipt-123");
        assertThat(result.success()).isTrue();
        assertThat(result.correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(result.failureReason()).isNull();
    }

    @Test
    @DisplayName("Deserializes failure result correctly with unknown fields")
    void shouldDeserializeFailureResultCorrectlyWithUnknownFields() throws Exception {
        String json = """
                {
                    "correlationId": "%s",
                    "providerCode": "MPESA",
                    "paymentReference": "NLJ7RT61SV",
                    "success": false,
                    "billingReceipt": null,
                    "failureReason": "Account not found"
                }
                """.formatted(CORRELATION_ID);

        var result = objectMapper.readValue(json, ProvisioningResultMessage.class);

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isEqualTo("Account not found");
        assertThat(result.billingReceipt()).isNull();
    }

    // =========================================================================
    // Compact constructor validation
    // =========================================================================

    @Test
    @DisplayName("Rejects null correlationId")
    void shouldRejectNullCorrelationId() {
        assertThatThrownBy(() -> new ProvisioningResultMessage(
                null, "NLJ7RT61SV", true, "receipt-123", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId");
    }

    @Test
    @DisplayName("Rejects null paymentReference")
    void shouldRejectNullPaymentReference() {
        assertThatThrownBy(() -> new ProvisioningResultMessage(
                CORRELATION_ID, null, true, "receipt-123", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paymentReference");
    }

    @Test
    @DisplayName("Rejects missing billingReceipt on success")
    void shouldRejectMissingBillingReceiptOnSuccess() {
        assertThatThrownBy(() -> new ProvisioningResultMessage(
                CORRELATION_ID, "NLJ7RT61SV", true, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("billingReceipt");
    }

    @Test
    @DisplayName("Rejects missing failureReason on failure")
    void shouldRejectMissingFailureReasonOnFailure() {
        assertThatThrownBy(() -> new ProvisioningResultMessage(
                CORRELATION_ID, "NLJ7RT61SV", false, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failureReason");
    }
}