package com.mycompany.api.mpesa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the M-Pesa Gateway microservice.
 *
 * <p>Handles Safaricom C2B callback ingestion, persists payment events to MongoDB,
 * and publishes provisioning requests via RabbitMQ using the Outbox Pattern.
 *
 * <p>{@code @EnableScheduling} is required for the {@code OutboxProcessor} polling loop.
 */
@SpringBootApplication
@EnableScheduling
public class MpesaGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MpesaGatewayApplication.class, args);
    }

}