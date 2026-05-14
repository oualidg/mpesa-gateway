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
 * Validates that the business short code matches the configured Safaricom
 * paybill shortcode for this gateway instance.
 *
 * <p>Null values are treated as valid — use {@code @NotBlank} separately.
 *
 * @author Oualid Gharach
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ShortCodeValidator.class)
@Documented
public @interface ValidShortCode {

    String message() default "BusinessShortCode does not match the configured paybill shortcode";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}