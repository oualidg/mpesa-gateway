/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 9:11 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP client configuration for outbound validation calls to the Utility Account (UA) Service.
 *
 * <p>Apache HttpClient 5 is used as the underlying HTTP client to provide explicit
 * connection pool control, aligned with the architecture specification
 * (pool size: 20, max per route: 20).
 *
 * <p>Three timeout boundaries are enforced per Convention §21.3:
 * <ul>
 *   <li>{@code connectTimeout} — time to establish a TCP connection (200ms)</li>
 *   <li>{@code connectionRequestTimeout} — time to acquire a connection from the pool;
 *       prevents indefinite thread blocking under pool exhaustion (500ms)</li>
 *   <li>{@code readTimeout} — time to receive a response after connection is established
 *       (3,000ms)</li>
 * </ul>
 *
 * <p>All timeout and pool configuration is bound from {@link UaServiceProperties} —
 * no values are hardcoded here.
 *
 * <p>The {@code X-Api-Key} header is set at call time in {@code UaValidationClient}
 * using the API key from {@link UaServiceProperties} — not here, to avoid exposing
 * it as a default header on all requests.
 *
 * <p>Resilience4j CircuitBreaker is applied at the call site in
 * {@code UaValidationClient}, not here.
 *
 * @author Oualid Gharach
 */
@Configuration
public class RestClientConfig {

    /**
     * RestClient configured for outbound UA Service validation calls.
     *
     * <p>Apache HttpClient 5 backs the client with explicit connection pool
     * and timeout configuration. Automatic retries are disabled — retry logic
     * is handled by Resilience4j in {@code UaValidationClient}.
     *
     * @param properties validated UA Service connection properties
     * @return configured {@link RestClient}
     */
    @Bean
    public RestClient uaServiceRestClient(UaServiceProperties properties) {
        PoolingHttpClientConnectionManager connectionManager =
                new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(properties.poolSize());
        connectionManager.setDefaultMaxPerRoute(properties.maxConnectionsPerRoute());
        connectionManager.setDefaultConnectionConfig(
                ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.of(properties.connectTimeout()))
                        .build()
        );

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(properties.connectionRequestTimeout()))
                .setResponseTimeout(Timeout.of(properties.readTimeout()))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .evictExpiredConnections()
                .evictIdleConnections(Timeout.of(properties.idleConnectionEvictDuration()))
                .build();

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }
}