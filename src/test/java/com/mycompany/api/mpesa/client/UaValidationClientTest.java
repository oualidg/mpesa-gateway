/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/14/2026 at 8:44 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.client;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.mycompany.api.mpesa.config.UaServiceProperties;
import com.mycompany.api.mpesa.dto.ValidationResponse;
import com.mycompany.api.mpesa.enums.ValidationResultCode;
import com.mycompany.api.mpesa.service.ValidationFallbackService;
import com.mycompany.api.mpesa.util.BillRefNormaliser;
import com.mycompany.api.mpesa.util.BillRefNormaliser.BillRefStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * WireMock-based tests for {@link UaValidationClient}.
 *
 * <p>Tests HTTP status code mapping and endpoint path resolution.
 * All stubs require the {@code X-Api-Key} header — requests without it
 * find no matching stub and return 404, failing the test. This implicitly
 * validates header propagation across all scenarios.
 *
 * <p>Circuit breaker and fallback are not tested here — Spring AOP proxy
 * is not active outside the Spring context.
 *
 * @author Oualid Gharach
 */
@ExtendWith(MockitoExtension.class)
class UaValidationClientTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final String ACCOUNT_VALIDATE_PATH  = "/api/v1/accounts/1234567897/validate";
    private static final String CUSTOMER_VALIDATE_PATH = "/api/v1/customers/12345674/validate";
    private static final String TEST_API_KEY = "test-api-key";

    @Mock
    private ValidationFallbackService fallbackService;

    private UaValidationClient uaValidationClient;
    private BillRefStrategy accountStrategy;
    private BillRefStrategy customerStrategy;

    @BeforeEach
    void setUp() {
        UaServiceProperties properties = new UaServiceProperties(
                wireMock.baseUrl(),
                TEST_API_KEY,
                Duration.ofMillis(200),
                Duration.ofSeconds(3),
                Duration.ofMillis(500),
                5,
                5,
                Duration.ofSeconds(30)
        );

        RestClient restClient = RestClient.builder()
                .baseUrl(wireMock.baseUrl())
                .requestFactory(new HttpComponentsClientHttpRequestFactory(
                        HttpClients.createDefault()))
                .build();

        uaValidationClient = new UaValidationClient(restClient, properties, fallbackService);

        accountStrategy = BillRefNormaliser.normalise("1234567897").orElseThrow();
        customerStrategy = BillRefNormaliser.normalise("12345674").orElseThrow();
    }

    // =========================================================================
    // Status Code Mapping
    // =========================================================================

    @Test
    @DisplayName("Returns accepted response for HTTP 200")
    void shouldReturnAcceptedForHttp200() {
        stubValidation(ACCOUNT_VALIDATE_PATH, 200);

        ValidationResponse response = uaValidationClient.validate(accountStrategy);

        assertThat(response.resultCode()).isEqualTo(ValidationResultCode.ACCEPTED.code());
    }

    @Test
    @DisplayName("Returns invalid account response for HTTP 404")
    void shouldReturnInvalidAccountForHttp404() {
        stubValidation(ACCOUNT_VALIDATE_PATH, 404);

        ValidationResponse response = uaValidationClient.validate(accountStrategy);

        assertThat(response.resultCode()).isEqualTo(ValidationResultCode.INVALID_ACCOUNT.code());
    }

    @Test
    @DisplayName("Returns invalid account response for HTTP 409")
    void shouldReturnInvalidAccountForHttp409() {
        stubValidation(ACCOUNT_VALIDATE_PATH, 409);

        ValidationResponse response = uaValidationClient.validate(accountStrategy);

        assertThat(response.resultCode()).isEqualTo(ValidationResultCode.INVALID_ACCOUNT.code());
    }

    @Test
    @DisplayName("Returns other error response for unexpected HTTP status")
    void shouldReturnOtherErrorForUnexpectedStatus() {
        stubValidation(ACCOUNT_VALIDATE_PATH, 503);

        ValidationResponse response = uaValidationClient.validate(accountStrategy);

        assertThat(response.resultCode()).isEqualTo(ValidationResultCode.OTHER_ERROR.code());
    }

    // =========================================================================
    // Endpoint Path Resolution
    // =========================================================================

    @Test
    @DisplayName("Calls account validation endpoint for account strategy")
    void shouldCallAccountEndpointForAccountStrategy() {
        stubValidation(ACCOUNT_VALIDATE_PATH, 200);

        uaValidationClient.validate(accountStrategy);

        wireMock.verify(getRequestedFor(urlPathEqualTo(ACCOUNT_VALIDATE_PATH))
                .withHeader("X-Api-Key", equalTo(TEST_API_KEY)));
    }

    @Test
    @DisplayName("Calls customer validation endpoint for customer strategy")
    void shouldCallCustomerEndpointForCustomerStrategy() {
        stubValidation(CUSTOMER_VALIDATE_PATH, 200);

        uaValidationClient.validate(customerStrategy);

        wireMock.verify(getRequestedFor(urlPathEqualTo(CUSTOMER_VALIDATE_PATH))
                .withHeader("X-Api-Key", equalTo(TEST_API_KEY)));
    }

    @Test
    @DisplayName("Forwards X-Correlation-ID header to UA Service")
    void shouldForwardCorrelationIdHeader() {
        stubValidation(ACCOUNT_VALIDATE_PATH, 200);
        MDC.put("correlationId", "test-correlation-id");

        try {
            uaValidationClient.validate(accountStrategy);

            wireMock.verify(getRequestedFor(urlPathEqualTo(ACCOUNT_VALIDATE_PATH))
                    .withHeader("X-Correlation-ID", equalTo("test-correlation-id")));
        } finally {
            MDC.remove("correlationId");
        }
    }

    // =========================================================================
    // Stub helpers
    // =========================================================================

    private void stubValidation(String path, int status) {
        wireMock.stubFor(get(urlPathEqualTo(path))
                .withHeader("X-Api-Key", equalTo(TEST_API_KEY))
                .willReturn(aResponse().withStatus(status)));
    }
}