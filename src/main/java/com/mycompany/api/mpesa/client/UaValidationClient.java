/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 9:20 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.client;

import com.mycompany.api.mpesa.config.UaServiceProperties;
import com.mycompany.api.mpesa.dto.ValidationResponse;
import com.mycompany.api.mpesa.enums.ValidationResultCode;
import com.mycompany.api.mpesa.service.ValidationFallbackService;
import com.mycompany.api.mpesa.util.BillRefNormaliser.BillRefStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Outbound HTTP client for UA Service account and customer validation calls.
 *
 * <p>Calls the appropriate UA Service validation endpoint via the {@link BillRefStrategy}
 * and maps the HTTP response to a Safaricom {@link ValidationResponse}.
 *
 * <p>A Resilience4j circuit breaker ({@code ua-validation}) wraps all outbound
 * calls. When the circuit is open, the fallback method delegates to
 * {@link ValidationFallbackService} which checks recent successfully posted events
 * within the configured time window (ADR-006).
 *
 * <p>Extracted as a separate {@code @Component} from
 * {@link com.mycompany.api.mpesa.service.ValidationService} to ensure Spring AOP
 * proxy interception works correctly for {@code @CircuitBreaker}. Self-invocation
 * within the same bean bypasses the proxy and silently disables the circuit breaker
 * (Convention §9.5).
 *
 * <p>UA Service validation endpoints return HTTP status only — no response body:
 * <ul>
 *   <li>HTTP 200 → valid and acceptable</li>
 *   <li>HTTP 404 → not found</li>
 *   <li>HTTP 409 → exists but not valid for payment</li>
 * </ul>
 *
 * @author Oualid Gharach
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UaValidationClient {

    private final RestClient uaServiceRestClient;
    private final UaServiceProperties uaServiceProperties;
    private final ValidationFallbackService fallbackService;

    private static final String CIRCUIT_BREAKER_NAME = "ua-validation";

    /**
     * Validates a resolved bill reference against the UA Service.
     *
     * <p>The {@link BillRefStrategy} provides the endpoint path and reference value —
     * no type inspection required in this class.
     *
     * <p>When the circuit breaker is open, {@link #validateFallback} is invoked.
     *
     * @param strategy the resolved bill reference strategy
     * @return {@link ValidationResponse#accepted()} or an appropriate rejection response
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "validateFallback")
    public ValidationResponse validate(BillRefStrategy strategy) {
        log.info("Calling UA Service validation. type={} reference={}",
                strategy.type(), strategy.reference());

        try {
            ResponseEntity<Void> response = uaServiceRestClient.get()
                    .uri(strategy.path(), strategy.reference())
                    .header("X-Api-Key", uaServiceProperties.apiKey())
                    .header("X-Correlation-ID", MDC.get("correlationId"))
                    .retrieve()
                    .toBodilessEntity();

            return mapUaResponse(response.getStatusCode());

        } catch (RestClientResponseException e) {
            return mapUaResponse(e.getStatusCode());
        }
    }

    /**
     * Fallback method invoked when the UA Service circuit breaker is open.
     *
     * <p>Delegates to {@link ValidationFallbackService} which checks recent
     * successfully posted events within the configured time window (ADR-006).
     *
     * @param strategy  the resolved bill reference strategy
     * @param throwable the exception that triggered the fallback
     * @return {@link ValidationResponse} based on recent event history
     */
    public ValidationResponse validateFallback(BillRefStrategy strategy, Throwable throwable) {
        log.warn("UA Service circuit breaker open — applying validation fallback. reason={}",
                throwable.getMessage());
        return fallbackService.validate(strategy);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Maps a UA Service HTTP status code to a Safaricom {@link ValidationResponse}.
     *
     * @param status the HTTP status code from the UA Service
     * @return mapped {@link ValidationResponse}
     */
    private ValidationResponse mapUaResponse(HttpStatusCode status) {
        return switch (status.value()) {
            case 200 -> ValidationResponse.accepted();
            case 404 -> {
                log.warn("UA Service validation — account or customer not found.");
                yield ValidationResponse.rejected(
                        ValidationResultCode.INVALID_ACCOUNT.code(),
                        ValidationResultCode.INVALID_ACCOUNT.description());
            }
            case 409 -> {
                log.warn("UA Service validation — account exists but not valid for payment.");
                yield ValidationResponse.rejected(
                        ValidationResultCode.INVALID_ACCOUNT.code(),
                        ValidationResultCode.INVALID_ACCOUNT.description());
            }
            default -> {
                log.warn("UA Service validation — unexpected status. status={}", status);
                yield ValidationResponse.rejected(
                        ValidationResultCode.OTHER_ERROR.code(),
                        ValidationResultCode.OTHER_ERROR.description());
            }
        };
    }
}