/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/19/2026 at 1:05 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.metrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that records Micrometer metrics for inbound Safaricom C2B
 * confirmation callbacks without modifying {@code ConfirmationController}.
 *
 * <p>Wraps {@code ConfirmationController.confirmation()} with a latency timer
 * and increments the appropriate outcome counter based on the HTTP status of
 * the returned {@link ResponseEntity}. A 2xx response is recorded as
 * {@code success}; anything else is recorded as {@code failure}.
 *
 * @author Oualid Gharach
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ConfirmationMetricsAspect {

    private final MpesaCallbackMetrics callbackMetrics;

    /**
     * Times the confirmation ingest and records the outcome.
     *
     * @param pjp the proceeding join point
     * @return the {@link ResponseEntity} returned by the controller
     * @throws Throwable if the controller throws unexpectedly
     */
    @Around("execution(* com.mycompany.api.mpesa.controller.ConfirmationController.confirmation(..))")
    public Object recordConfirmationMetrics(ProceedingJoinPoint pjp) throws Throwable {
        return callbackMetrics.latencyTimer().record(() -> {
            try {
                ResponseEntity<?> response = (ResponseEntity<?>) pjp.proceed();
                if (response.getStatusCode().is2xxSuccessful()) {
                    callbackMetrics.recordSuccess();
                } else {
                    callbackMetrics.recordFailure();
                }
                return response;
            } catch (Throwable t) {
                callbackMetrics.recordFailure();
                throw new RuntimeException(t);
            }
        });
    }
}