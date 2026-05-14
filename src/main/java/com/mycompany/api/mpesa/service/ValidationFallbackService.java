/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/14/2026 at 11:00 AM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.service;

import com.mycompany.api.mpesa.config.ValidationFallbackProperties;
import com.mycompany.api.mpesa.dto.ValidationResponse;
import com.mycompany.api.mpesa.enums.MpesaEventState;
import com.mycompany.api.mpesa.enums.ValidationResultCode;
import com.mycompany.api.mpesa.repository.MpesaEventRepository;
import com.mycompany.api.mpesa.util.BillRefNormaliser.BillRefStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Fallback validation service used when the UA Service circuit breaker is open (ADR-006).
 *
 * <p>When the UA Service is unavailable, the primary validation path is blocked.
 * Rather than rejecting all validation requests during outages, this service checks
 * whether the bill reference has a successfully posted event within a configurable
 * time window. If found, the reference is treated as valid — it has been successfully
 * processed recently and is therefore trusted for validation purposes.
 *
 * <p>Limitations of this approach:
 * <ul>
 *   <li>References without recent history may be rejected — including first-time payers.
 *       This is an accepted trade-off of using recent successful payments as a proxy
 *       for account validity during UA Service unavailability.</li>
 *   <li>The fallback window is configurable via {@code app.validation.fallback.window}.</li>
 *   <li>The fallback is disabled when {@code app.validation.fallback.enabled=false} —
 *       all requests are rejected when the circuit is open.</li>
 * </ul>
 *
 * @author Oualid Gharach
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationFallbackService {

    private final MpesaEventRepository mpesaEventRepository;
    private final ValidationFallbackProperties fallbackProperties;

    /**
     * Validates a bill reference using recent successfully posted events.
     *
     * <p>If fallback is disabled — rejects immediately. Otherwise checks for a
     * {@code POSTED} event matching the bill reference within the configured
     * time window. Accepts if found, rejects if not.
     *
     * @param strategy the resolved bill reference strategy
     * @return {@link ValidationResponse#accepted()} if a recent posted event exists,
     *         or an appropriate rejection response
     */
    public ValidationResponse validate(BillRefStrategy strategy) {
        if (fallbackProperties.enabled()) {
            Instant since = Instant.now().minus(fallbackProperties.window());

            boolean hasRecentPostedEvent = mpesaEventRepository
                    .existsByBillRefNumberAndStateAndCreatedAtAfter(
                            strategy.reference(),
                            MpesaEventState.POSTED,
                            since);

            if (hasRecentPostedEvent) {
                log.info("Validation fallback — recent posted event found, accepting. reference={}",
                        strategy.reference());
                return ValidationResponse.accepted();
            }
        }

        log.warn("Validation fallback — rejecting. reference={} fallbackEnabled={}",
                strategy.reference(), fallbackProperties.enabled());
        return ValidationResponse.rejected(
                ValidationResultCode.OTHER_ERROR.code(),
                ValidationResultCode.OTHER_ERROR.description());
    }
}