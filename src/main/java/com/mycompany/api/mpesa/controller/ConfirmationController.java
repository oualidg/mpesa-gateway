/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 7:29 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.controller;

import com.mycompany.api.mpesa.dto.CallbackRequest;
import com.mycompany.api.mpesa.service.ConfirmationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that handles Safaricom C2B confirmation callbacks.
 *
 * <p>The confirmation callback is received after funds have been collected.
 * The controller delegates immediately to {@link ConfirmationService} and
 * returns HTTP 200 after durable persistence, or HTTP 500 if persistence
 * fails so Safaricom retries the callback.
 *
 * <p>No {@code @Valid} is used — all callbacks are passed through to the
 * service regardless of payload validity. The service validates and persists
 * invalid events as {@code SUSPENDED} rather than rejecting them outright,
 * ensuring no callback is ever lost.
 *
 * <p>Callback token authentication is enforced upstream by
 * {@code CallbackTokenFilter} before this controller is reached.
 *
 * @author Oualid Gharach
 */
@Slf4j
@RestController
@RequestMapping("/mpesa/v1")
@RequiredArgsConstructor
@Tag(name = "M-Pesa Confirmation", description = "Safaricom C2B confirmation callback endpoint")
public class ConfirmationController {

    private final ConfirmationService confirmationService;

    /**
     * Receives a Safaricom C2B confirmation callback.
     *
     * <p>Returns HTTP 200 after the event is durably persisted — either as
     * {@code RECEIVED} for valid events or {@code SUSPENDED} for invalid ones.
     * Returns HTTP 500 if MongoDB persistence fails entirely, signalling
     * Safaricom to retry the callback.
     *
     * @param request the Safaricom C2B confirmation payload
     * @return HTTP 200 on success, HTTP 500 on persistence failure
     */
    @PostMapping("/confirmation")
    @Operation(
            summary = "Safaricom C2B confirmation callback",
            description = "Receives a confirmed M-Pesa payment event. Persists the event durably " +
                    "before returning. Returns HTTP 200 on success, HTTP 500 if persistence " +
                    "fails so Safaricom retries."
    )
    public ResponseEntity<Void> confirmation(@RequestBody CallbackRequest request) {
        try {
            confirmationService.ingest(request);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("CRITICAL — confirmation persistence failed, returning HTTP 500 for Safaricom retry. reason={}",
                    e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}