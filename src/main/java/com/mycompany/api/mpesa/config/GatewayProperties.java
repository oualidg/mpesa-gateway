/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 7:42 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Gateway-specific configuration properties.
 *
 * <p>Bound from the {@code app.gateway} prefix in application properties.
 * The service fails at startup if any required property is missing.
 *
 * @param shortCode the configured Safaricom paybill shortcode — all inbound
 *                  callbacks must carry this value in {@code BusinessShortCode}
 *
 * @author Oualid Gharach
 */
@Validated
@ConfigurationProperties(prefix = "app.gateway")
public record GatewayProperties(
        @NotBlank String shortCode
) {}