/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 5:43 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration properties for the validation fallback (ADR-006).
 *
 * <p>When the UA Service circuit breaker is open, the Gateway falls back to
 * checking recent successfully posted M-Pesa events within a configurable
 * time window. If a matching event exists within the window, the reference
 * is treated as valid for validation purposes.
 *
 * <p>Bound from the {@code app.validation.fallback} prefix in application properties.
 *
 * @param enabled whether the validation fallback is active
 * @param window  how far back to look for successfully posted events
 *
 * @author Oualid Gharach
 */
@Validated
@ConfigurationProperties(prefix = "app.validation.fallback")
public record ValidationFallbackProperties(
        boolean enabled,
        @NotNull Duration window
) {}