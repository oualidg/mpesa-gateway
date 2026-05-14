/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 7:44 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.util;

import java.util.Optional;

/**
 * Utility class for normalising and resolving Safaricom {@code BillRefNumber} values.
 *
 * <p>The bill reference is a free-text field entered by the subscriber. The Gateway
 * resolves it to either an account number or a customer number using the following rules:
 * <ul>
 *   <li>10-digit numeric value passing Luhn validation → account number</li>
 *   <li>8-digit numeric value passing Luhn validation → customer number</li>
 *   <li>Any other value → invalid</li>
 * </ul>
 *
 * <p>{@link #normalise(String)} returns an {@link Optional} of {@link BillRefStrategy} —
 * empty if the reference is invalid, or a strategy that carries the resolved reference
 * type, value, and UA Service validation path. The strategy is the entry point for
 * downstream processing — callers invoke {@link BillRefStrategy#path()} and
 * {@link BillRefStrategy#reference()} directly without inspecting null fields,
 * boolean flags, or switch expressions.
 *
 * <p>All methods are static — this class is a pure stateless utility and is
 * not a Spring component.
 *
 * @author Oualid Gharach
 */
public class BillRefNormaliser {

    private BillRefNormaliser() {}

    /**
     * Represents the type of a resolved bill reference.
     */
    public enum ReferenceType {
        /** Resolved to a 10-digit account number. */
        ACCOUNT,
        /** Resolved to an 8-digit customer number. */
        CUSTOMER
    }

    /**
     * Strategy object representing a successfully resolved bill reference.
     *
     * <p>Carries the resolved {@link ReferenceType}, the reference value, and
     * the UA Service validation path. Callers use {@link #path()} and
     * {@link #reference()} directly — no switch expressions or null checks required.
     *
     * <p>Validation paths are UA Service API contract constants — they are
     * not environment-specific and belong here rather than in application properties.
     *
     * @param type      the resolved reference type
     * @param reference the resolved reference value
     */
    public record BillRefStrategy(
            ReferenceType type,
            String reference
    ) {
        private static final String ACCOUNT_VALIDATE_PATH = "/api/v1/accounts/{ref}/validate";
        private static final String CUSTOMER_VALIDATE_PATH = "/api/v1/customers/{ref}/validate";

        /**
         * Returns the UA Service validation path for this reference type.
         *
         * @return the validation endpoint path
         */
        public String path() {
            return switch (type) {
                case ACCOUNT -> ACCOUNT_VALIDATE_PATH;
                case CUSTOMER -> CUSTOMER_VALIDATE_PATH;
            };
        }
    }

    /**
     * Normalises a {@code BillRefNumber} into a {@link BillRefStrategy}.
     *
     * <p>Input is expected to be already trimmed — the mapper normalises all
     * string fields before this method is called from the confirmation or
     * validation path.
     *
     * <p>Returns {@link Optional#empty()} if the value is null, blank,
     * non-numeric, wrong length, or fails Luhn validation.
     *
     * @param billRefNumber the bill reference from the Safaricom callback
     * @return an {@link Optional} containing the resolved {@link BillRefStrategy},
     *         or empty if the reference is invalid
     */
    public static Optional<BillRefStrategy> normalise(String billRefNumber) {
        if (billRefNumber == null || billRefNumber.isBlank()) {
            return Optional.empty();
        }

        if (!billRefNumber.matches("\\d+")) {
            return Optional.empty();
        }

        if (!isValidLuhn(billRefNumber)) {
            return Optional.empty();
        }

        return switch (billRefNumber.length()) {
            case 8 -> Optional.of(new BillRefStrategy(ReferenceType.CUSTOMER, billRefNumber));
            case 10 -> Optional.of(new BillRefStrategy(ReferenceType.ACCOUNT, billRefNumber));
            default -> Optional.empty();
        };
    }

    /**
     * Validates a numeric string against the Luhn algorithm.
     *
     * <p>The Luhn algorithm doubles every second digit from the right,
     * subtracts 9 from any result greater than 9, and checks that the
     * total sum is divisible by 10.
     *
     * @param number the numeric string to validate
     * @return {@code true} if the string passes Luhn validation
     */
    public static boolean isValidLuhn(String number) {
        int sum = 0;
        boolean alternate = false;

        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(number.charAt(i));

            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }

            sum += digit;
            alternate = !alternate;
        }

        return sum % 10 == 0;
    }
}