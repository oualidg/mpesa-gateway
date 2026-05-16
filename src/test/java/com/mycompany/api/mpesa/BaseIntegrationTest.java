/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/14/2026 at 9:40 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa;

import com.mycompany.api.mpesa.client.UaValidationClient;
import com.mycompany.api.mpesa.messaging.MpesaResultListener;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests requiring a MongoDB instance.
 *
 * <p>Provides a shared static {@link MongoDBContainer} reused across all
 * integration test classes — started once per test suite, not per class.
 *
 * <p>Resilience4j circuit breaker autoconfiguration is excluded — integration
 * tests focus on MongoDB persistence behaviour and do not require circuit
 * breaker wiring.
 *
 * <p>{@link UaValidationClient} is mocked — outbound UA Service calls are not
 * required for MongoDB persistence tests.
 *
 * @author Oualid Gharach
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@EnableAutoConfiguration(exclude = {
        io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration.class,
        io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerMetricsAutoConfiguration.class
})
public abstract class BaseIntegrationTest {

    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8");

    static {
        MONGO.start();
    }

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () ->
                MONGO.getConnectionString() + "/mpesa");
    }

    @MockitoBean
    protected UaValidationClient uaValidationClient;

    @MockitoBean
    protected MpesaResultListener mpesaResultListener;
}