/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 2:05 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers all application-level {@code @ConfigurationProperties} records
 * for the M-Pesa Gateway.
 *
 * <p>Messaging properties are excluded — they are registered in
 * {@link MessagingConfig} where they are tightly coupled to RabbitMQ bean setup.
 *
 * <p>Tracing constants are centralised here to ensure consistent MDC key names
 * and HTTP header names across all components.
 *
 * @author Oualid Gharach
 */
@Configuration
@EnableConfigurationProperties({
        CallbackProperties.class,
        UaServiceProperties.class,
        OutboxProperties.class,
        ValidationFallbackProperties.class,
        GatewayProperties.class
})
public class AppConfig {
    public static final String MDC_CORRELATION_ID    = "correlationId";
    public static final String MDC_TRANS_ID          = "transId";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String PROVIDER_CODE = "MPESA";
}