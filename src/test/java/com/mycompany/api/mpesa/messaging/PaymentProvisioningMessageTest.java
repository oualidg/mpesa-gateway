/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/15/2026 at 9:02 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PaymentProvisioningMessage} compact constructor validation.
 *
 * <p>Verifies that the record enforces its invariants regardless of caller.
 *
 * @author Oualid Gharach
 */
class PaymentProvisioningMessageTest {

    private static final UUID CORRELATION_ID = UUID.randomUUID();
    private static final String PROVIDER_CODE = "MPESA";
    private static final String PAYMENT_REFERENCE = "NLJ7RT61SV";
    private static final BigDecimal AMOUNT = new BigDecimal("1500.00");
    private static final String ACCOUNT_NUMBER = "1234567897";
    private static final String REPLY_TO = "mpesa.result";

    // =========================================================================
    // Valid construction
    // =========================================================================

    @Test
    @DisplayName("Creates message successfully with account number")
    void shouldCreateMessageWithAccountNumber() {
        var message = new PaymentProvisioningMessage(
                CORRELATION_ID, PROVIDER_CODE, PAYMENT_REFERENCE,
                AMOUNT, ACCOUNT_NUMBER, null, REPLY_TO);

        assertThat(message.accountNumber()).isEqualTo(ACCOUNT_NUMBER);
        assertThat(message.customerNumber()).isNull();
    }

    @Test
    @DisplayName("Creates message successfully with customer number")
    void shouldCreateMessageWithCustomerNumber() {
        var message = new PaymentProvisioningMessage(
                CORRELATION_ID, PROVIDER_CODE, PAYMENT_REFERENCE,
                AMOUNT, null, "12345674", REPLY_TO);

        assertThat(message.customerNumber()).isEqualTo("12345674");
        assertThat(message.accountNumber()).isNull();
    }

    // =========================================================================
    // Null / blank field validation
    // =========================================================================

    @Test
    @DisplayName("Rejects null correlationId")
    void shouldRejectNullCorrelationId() {
        assertThatThrownBy(() -> new PaymentProvisioningMessage(
                null, PROVIDER_CODE, PAYMENT_REFERENCE,
                AMOUNT, ACCOUNT_NUMBER, null, REPLY_TO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId");
    }

    @Test
    @DisplayName("Rejects null providerCode")
    void shouldRejectNullProviderCode() {
        assertThatThrownBy(() -> new PaymentProvisioningMessage(
                CORRELATION_ID, null, PAYMENT_REFERENCE,
                AMOUNT, ACCOUNT_NUMBER, null, REPLY_TO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerCode");
    }

    @Test
    @DisplayName("Rejects blank providerCode")
    void shouldRejectBlankProviderCode() {
        assertThatThrownBy(() -> new PaymentProvisioningMessage(
                CORRELATION_ID, "  ", PAYMENT_REFERENCE,
                AMOUNT, ACCOUNT_NUMBER, null, REPLY_TO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerCode");
    }

    @Test
    @DisplayName("Rejects null paymentReference")
    void shouldRejectNullPaymentReference() {
        assertThatThrownBy(() -> new PaymentProvisioningMessage(
                CORRELATION_ID, PROVIDER_CODE, null,
                AMOUNT, ACCOUNT_NUMBER, null, REPLY_TO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paymentReference");
    }

    @Test
    @DisplayName("Rejects null amount")
    void shouldRejectNullAmount() {
        assertThatThrownBy(() -> new PaymentProvisioningMessage(
                CORRELATION_ID, PROVIDER_CODE, PAYMENT_REFERENCE,
                null, ACCOUNT_NUMBER, null, REPLY_TO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("Rejects zero amount")
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> new PaymentProvisioningMessage(
                CORRELATION_ID, PROVIDER_CODE, PAYMENT_REFERENCE,
                BigDecimal.ZERO, ACCOUNT_NUMBER, null, REPLY_TO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("Rejects null replyToRoutingKey")
    void shouldRejectNullReplyToRoutingKey() {
        assertThatThrownBy(() -> new PaymentProvisioningMessage(
                CORRELATION_ID, PROVIDER_CODE, PAYMENT_REFERENCE,
                AMOUNT, ACCOUNT_NUMBER, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("replyToRoutingKey");
    }

    // =========================================================================
    // Reference invariant
    // =========================================================================

    @Test
    @DisplayName("Rejects when both accountNumber and customerNumber are set")
    void shouldRejectWhenBothReferenceTypesSet() {
        assertThatThrownBy(() -> new PaymentProvisioningMessage(
                CORRELATION_ID, PROVIDER_CODE, PAYMENT_REFERENCE,
                AMOUNT, ACCOUNT_NUMBER, "12345674", REPLY_TO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Exactly one");
    }

    @Test
    @DisplayName("Rejects when neither accountNumber nor customerNumber is set")
    void shouldRejectWhenNeitherReferenceTypeSet() {
        assertThatThrownBy(() -> new PaymentProvisioningMessage(
                CORRELATION_ID, PROVIDER_CODE, PAYMENT_REFERENCE,
                AMOUNT, null, null, REPLY_TO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Exactly one");
    }
}