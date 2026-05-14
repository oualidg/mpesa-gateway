/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 1:39 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Safaricom callback security.
 *
 * <p>Bound from the {@code app.callback} prefix in application properties.
 * The token is injected at runtime from the deployment environment and must
 * never be committed to source control.
 *
 * <p>The service fails at startup if the token is missing or blank — a Gateway
 * running without a callback token would accept requests from any caller.
 *
 * @param token the secret token that must be present as a query parameter
 *              on all inbound Safaricom callback requests
 *
 * @author Oualid Gharach
 */
@Validated
@ConfigurationProperties(prefix = "app.callback")
public record CallbackProperties(
        @NotBlank String token
) {}