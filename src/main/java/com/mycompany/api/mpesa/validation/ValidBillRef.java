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

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that a bill reference number is either an 8-digit customer number
 * or a 10-digit account number, both passing Luhn checksum validation.
 *
 * <p>Null values are treated as valid — use {@code @NotBlank} separately if
 * the field is required.
 *
 * @author Oualid Gharach
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BillRefValidator.class)
@Documented
public @interface ValidBillRef {

    String message() default "BillRefNumber must be an 8-digit customer number or 10-digit account number with valid Luhn checksum";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}