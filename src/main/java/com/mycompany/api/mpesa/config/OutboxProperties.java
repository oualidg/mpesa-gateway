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
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration properties for the outbox processor polling loop.
 *
 * <p>Bound from the {@code app.outbox} prefix in application properties.
 * All fields are required — the service will not start if any are missing or invalid.
 *
 * @param pollInterval    how frequently the outbox processor polls for unsent entries
 * @param batchSize       maximum number of entries processed per poll cycle
 * @param alertThreshold  duration after which unsent entries trigger an alert
 *
 * @author Oualid Gharach
 */
@Validated
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        @NotNull Duration pollInterval,
        @Positive int batchSize,
        @NotNull Duration alertThreshold
) {}