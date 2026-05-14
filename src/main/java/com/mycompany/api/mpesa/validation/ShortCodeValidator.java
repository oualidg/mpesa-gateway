/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 7:39 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.validation;

import com.mycompany.api.mpesa.config.GatewayProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validator implementation for the {@link ValidShortCode} annotation.
 *
 * <p>Compares the incoming {@code BusinessShortCode} against the configured
 * paybill shortcode from {@link GatewayProperties}. This validator is a Spring
 * component to support property injection — Jakarta validators are Spring-managed
 * when used with Spring Boot's validation auto-configuration.
 *
 * <p>Null values are treated as valid — use {@code @NotBlank} separately.
 *
 * @author Oualid Gharach
 */
@Component
@RequiredArgsConstructor
public class ShortCodeValidator implements ConstraintValidator<ValidShortCode, String> {

    private final GatewayProperties gatewayProperties;

    /**
     * Validates that the short code matches the configured paybill shortcode.
     *
     * @param value   the short code to validate
     * @param context the constraint validator context
     * @return {@code true} if null or matches the configured shortcode
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.equals(gatewayProperties.shortCode());
    }
}