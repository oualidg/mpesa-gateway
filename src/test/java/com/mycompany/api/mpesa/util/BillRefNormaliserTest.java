/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/14/2026 at 8:24 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.util;

import com.mycompany.api.mpesa.util.BillRefNormaliser.BillRefStrategy;
import com.mycompany.api.mpesa.util.BillRefNormaliser.ReferenceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BillRefNormaliser}.
 *
 * <p>Covers normalisation rules, Luhn validation, and edge cases.
 * No Spring context required — pure logic tests.
 *
 * @author Oualid Gharach
 */
class BillRefNormaliserTest {

    // Valid Luhn numbers used across tests
    private static final String VALID_ACCOUNT = "1234567897";   // 10-digit, valid Luhn
    private static final String VALID_CUSTOMER = "12345674";    // 8-digit, valid Luhn

    // =========================================================================
    // Account Number Resolution
    // =========================================================================

    @Test
    @DisplayName("Resolves 10-digit Luhn-valid number to account number")
    void shouldResolveValidAccountNumber() {
        Optional<BillRefStrategy> result = BillRefNormaliser.normalise(VALID_ACCOUNT);

        assertThat(result).isPresent();
        assertThat(result.get().type()).isEqualTo(ReferenceType.ACCOUNT);
        assertThat(result.get().reference()).isEqualTo(VALID_ACCOUNT);
    }

    @Test
    @DisplayName("Returns correct validation path for account number")
    void shouldReturnAccountValidationPath() {
        BillRefStrategy strategy = BillRefNormaliser.normalise(VALID_ACCOUNT).orElseThrow();

        assertThat(strategy.path()).contains("accounts");
        assertThat(strategy.path()).contains("validate");
    }

    // =========================================================================
    // Customer Number Resolution
    // =========================================================================

    @Test
    @DisplayName("Resolves 8-digit Luhn-valid number to customer number")
    void shouldResolveValidCustomerNumber() {
        Optional<BillRefStrategy> result = BillRefNormaliser.normalise(VALID_CUSTOMER);

        assertThat(result).isPresent();
        assertThat(result.get().type()).isEqualTo(ReferenceType.CUSTOMER);
        assertThat(result.get().reference()).isEqualTo(VALID_CUSTOMER);
    }

    @Test
    @DisplayName("Returns correct validation path for customer number")
    void shouldReturnCustomerValidationPath() {
        BillRefStrategy strategy = BillRefNormaliser.normalise(VALID_CUSTOMER).orElseThrow();

        assertThat(strategy.path()).contains("customers");
        assertThat(strategy.path()).contains("validate");
    }

    // =========================================================================
    // Invalid References
    // =========================================================================

    @Test
    @DisplayName("Returns empty for null bill reference")
    void shouldReturnEmptyForNull() {
        assertThat(BillRefNormaliser.normalise(null)).isEmpty();
    }

    @Test
    @DisplayName("Returns empty for blank bill reference")
    void shouldReturnEmptyForBlank() {
        assertThat(BillRefNormaliser.normalise("   ")).isEmpty();
    }

    @Test
    @DisplayName("Returns empty for empty bill reference")
    void shouldReturnEmptyForEmpty() {
        assertThat(BillRefNormaliser.normalise("")).isEmpty();
    }

    @Test
    @DisplayName("Returns empty for non-numeric bill reference")
    void shouldReturnEmptyForNonNumeric() {
        assertThat(BillRefNormaliser.normalise("ABC12345")).isEmpty();
    }

    @Test
    @DisplayName("Returns empty for alphanumeric bill reference")
    void shouldReturnEmptyForAlphanumeric() {
        assertThat(BillRefNormaliser.normalise("1234ABC567")).isEmpty();
    }

    @Test
    @DisplayName("Returns empty for 10-digit number failing Luhn validation")
    void shouldReturnEmptyForInvalidLuhnAccountNumber() {
        assertThat(BillRefNormaliser.normalise("1234567890")).isEmpty();
    }

    @Test
    @DisplayName("Returns empty for 8-digit number failing Luhn validation")
    void shouldReturnEmptyForInvalidLuhnCustomerNumber() {
        assertThat(BillRefNormaliser.normalise("12345678")).isEmpty();
    }

    @Test
    @DisplayName("Returns empty for 9-digit number even if Luhn-valid")
    void shouldReturnEmptyForNineDigitNumber() {
        assertThat(BillRefNormaliser.normalise("123456782")).isEmpty();
    }

    @Test
    @DisplayName("Returns empty for 11-digit number even if Luhn-valid")
    void shouldReturnEmptyForElevenDigitNumber() {
        assertThat(BillRefNormaliser.normalise("12345678970")).isEmpty();
    }

    // =========================================================================
    // Luhn Algorithm
    // =========================================================================

    @Test
    @DisplayName("isValidLuhn returns true for valid Luhn number")
    void shouldReturnTrueForValidLuhnNumber() {
        assertThat(BillRefNormaliser.isValidLuhn(VALID_ACCOUNT)).isTrue();
        assertThat(BillRefNormaliser.isValidLuhn(VALID_CUSTOMER)).isTrue();
    }

    @Test
    @DisplayName("isValidLuhn returns false for invalid Luhn number")
    void shouldReturnFalseForInvalidLuhnNumber() {
        assertThat(BillRefNormaliser.isValidLuhn("1234567890")).isFalse();
        assertThat(BillRefNormaliser.isValidLuhn("12345678")).isFalse();
    }
}