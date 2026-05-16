/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 8:57 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.service;

import com.mycompany.api.mpesa.client.UaValidationClient;
import com.mycompany.api.mpesa.dto.CallbackRequest;
import com.mycompany.api.mpesa.dto.ValidationResponse;
import com.mycompany.api.mpesa.util.BillRefNormaliser;
import com.mycompany.api.mpesa.util.BillRefNormaliser.BillRefStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import static com.mycompany.api.mpesa.config.AppConfig.MDC_TRANS_ID;

/**
 * Service responsible for handling Safaricom C2B validation callbacks.
 *
 * <p>Validation is a synchronous pre-payment check — no funds have been collected
 * and no event is persisted. The service normalises the bill reference and delegates
 * to {@link UaValidationClient} for the UA Service check.
 *
 * <p>Payload validation is handled upstream at the controller via {@code @Valid} —
 * by the time this service is called, critical fields are guaranteed valid.
 *
 * <p>Processing steps:
 * <ol>
 *   <li>Normalise {@code billRefNumber} via {@link BillRefNormaliser}</li>
 *   <li>Call UA Service validation endpoint via {@link UaValidationClient} —
 *       circuit breaker and fallback (ADR-006) are applied at the client level</li>
 *   <li>Return the mapped {@link ValidationResponse}</li>
 * </ol>
 *
 * <p>Must respond within 8 seconds — enforced via the configured timeout chain
 * on the UA Service HTTP client and circuit breaker.
 *
 * @author Oualid Gharach
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationService {

    private final UaValidationClient uaValidationClient;

    /**
     * Validates a Safaricom C2B payment request before funds are collected.
     *
     * <p>Always returns a {@link ValidationResponse} — never throws. Safaricom
     * requires HTTP 200 for all validation responses regardless of outcome.
     *
     * @param request the inbound Safaricom validation payload
     * @return {@link ValidationResponse#accepted()} or an appropriate rejection response
     */
    public ValidationResponse validate(CallbackRequest request) {
        MDC.put(MDC_TRANS_ID, request.transId());

        try {
            log.info("Received validation callback. shortCode={} billRef={}",
                    request.businessShortCode(), request.billRefNumber());

            BillRefStrategy strategy = BillRefNormaliser.normalise(request.billRefNumber()).orElseThrow();

            ValidationResponse response = uaValidationClient.validate(strategy);

            log.info("Validation completed. resultCode={}", response.resultCode());

            return response;

        } finally {
            MDC.remove(MDC_TRANS_ID);
        }
    }
}