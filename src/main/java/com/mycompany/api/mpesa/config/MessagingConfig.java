/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 1:30 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * RabbitMQ messaging configuration for the M-Pesa Gateway.
 *
 * <p>Configures JSON serialization for all inbound and outbound messages using
 * Jackson. Both the {@link RabbitTemplate} (for publishing) and the listener
 * container factory (for consumption) use the same {@link Jackson2JsonMessageConverter},
 * ensuring consistent message format across the payment processing pipeline.
 *
 * <p>Publisher confirms are enabled on the {@link RabbitTemplate} to support the
 * Outbox Pattern — the outbox processor marks an entry as sent only after the broker
 * acknowledges receipt of the message.
 *
 * <p>The listener container factory explicitly sets manual acknowledgement mode.
 * This is required because {@code MpesaResultListener} manually calls
 * {@code channel.basicAck()} and {@code channel.basicReject()} — the factory
 * must be configured in Java, not relied upon from application properties, because
 * defining a custom factory bean bypasses Spring Boot's property-based auto-configuration.
 *
 * <p>{@code mandatory=true} is set on the {@link RabbitTemplate} to ensure unroutable
 * messages are returned to the publisher rather than silently dropped.
 *
 * <p>Queue and exchange names are bound from application properties via
 * {@link MessagingProperties} and validated at startup — the service will fail
 * fast if any required property is missing.
 *
 * @author Oualid Gharach
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(MessagingConfig.MessagingProperties.class)
public class MessagingConfig {

    private final ConnectionFactory connectionFactory;

    /**
     * Jackson message converter configured for JSON serialization of all RabbitMQ messages.
     *
     * <p>JavaTimeModule is registered to handle {@code Instant} and other Java 8
     * date/time types without falling back to timestamps.
     *
     * @return configured {@link Jackson2JsonMessageConverter}
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }

    /**
     * RabbitTemplate configured with the JSON message converter and publisher confirms.
     *
     * <p>Publisher confirms are required by the outbox processor — an outbox entry
     * is marked as sent only after the broker acknowledges receipt of the published
     * provisioning message.
     *
     * <p>{@code mandatory=true} ensures unroutable messages are returned to the
     * publisher rather than silently dropped by the broker.
     *
     * @return configured {@link RabbitTemplate}
     */
    @Bean
    public RabbitTemplate rabbitTemplate() {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        template.setMandatory(true);
        template.setConfirmCallback((correlationData, ack, cause) -> {});
        return template;
    }

    /**
     * Listener container factory configured with the JSON message converter and
     * explicit manual acknowledgement mode.
     *
     * <p>Prefetch of 5 and concurrency of 1-2 are aligned with the spec for
     * {@code mpesa.results.queue} — result processing is lightweight but must
     * not overwhelm MongoDB on concurrent updates.
     *
     * @return configured {@link SimpleRabbitListenerContainerFactory}
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory() {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(5);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(2);
        return factory;
    }

    /**
     * Validated configuration properties for RabbitMQ topology names.
     *
     * <p>Bound from the {@code app.messaging} prefix in application properties.
     * All fields are required — the service will not start if any are missing.
     *
     * @param exchange                the exchange to which provisioning requests are published
     * @param provisioningRoutingKey  the routing key used when publishing provisioning requests
     * @param mpesaResultRoutingKey   the routing key set on outgoing messages for result reply routing
     * @param resultsQueue            the queue from which provisioning results are consumed
     */
    @Validated
    @ConfigurationProperties(prefix = "app.messaging")
    public record MessagingProperties(
            @NotBlank String exchange,
            @NotBlank String provisioningRoutingKey,
            @NotBlank String mpesaResultRoutingKey,
            @NotBlank String resultsQueue
    ) {}
}