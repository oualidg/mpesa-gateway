/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 7:38 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.validation;

import com.mycompany.api.mpesa.util.BillRefNormaliser;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator implementation for the {@link ValidBillRef} annotation.
 *
 * <p>Delegates to {@link BillRefNormaliser#normalise(String)} — a bill reference
 * is valid if and only if it resolves to either a valid 8-digit customer number
 * or a valid 10-digit account number with a correct Luhn checksum.
 *
 * <p>Null values are treated as valid — use {@code @NotBlank} separately.
 *
 * @author Oualid Gharach
 */
public class BillRefValidator implements ConstraintValidator<ValidBillRef, String> {

    /**
     * Validates the bill reference number.
     *
     * @param value   the bill reference to validate
     * @param context the constraint validator context
     * @return {@code true} if null or resolves to a valid account or customer number
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return BillRefNormaliser.normalise(value).isPresent();
    }
}