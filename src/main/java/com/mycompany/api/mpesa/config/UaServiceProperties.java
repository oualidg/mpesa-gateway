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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration properties for outbound calls to the Utility Account (UA) Service.
 *
 * <p>Bound from the {@code app.ua-service} prefix in application properties.
 * All fields are required — the service will not start if any are missing or invalid.
 *
 * @param baseUrl                  base URL of the UA Service
 * @param apiKey                   API key for X-Api-Key authentication
 * @param connectTimeout           time to establish a TCP connection
 * @param readTimeout              time to receive a response
 * @param connectionRequestTimeout time to acquire a connection from the pool
 * @param poolSize                 maximum total connections in the pool
 * @param maxConnectionsPerRoute   maximum connections per route
 *
 * @author Oualid Gharach
 */
@Validated
@ConfigurationProperties(prefix = "app.ua-service")
public record UaServiceProperties(
        @NotBlank String baseUrl,
        @NotBlank String apiKey,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration connectionRequestTimeout,
        @Positive int poolSize,
        @Positive int maxConnectionsPerRoute,
        @NotNull Duration idleConnectionEvictDuration
) {}