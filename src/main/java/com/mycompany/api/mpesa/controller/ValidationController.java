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
package com.mycompany.api.mpesa.controller;

import com.mycompany.api.mpesa.dto.CallbackRequest;
import com.mycompany.api.mpesa.dto.ValidationResponse;
import com.mycompany.api.mpesa.enums.ValidationResultCode;
import com.mycompany.api.mpesa.service.ValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller that handles Safaricom C2B validation callbacks.
 *
 * <p>The validation callback is received before funds are collected — it is a
 * synchronous pre-payment check. The controller delegates to {@link ValidationService}
 * and returns the appropriate Safaricom {@link ValidationResponse}.
 *
 * <p>Safaricom requires HTTP 200 for all validation responses regardless of
 * accept or reject outcome — the decision is communicated via {@code ResultCode}
 * in the response body. HTTP 200 with a non-zero {@code ResultCode} means rejection.
 *
 * <p>Payload validation is applied via {@code @Valid} — structural violations
 * (missing critical fields, invalid bill reference, shortcode mismatch) are caught
 * here and returned as a Safaricom rejection response without reaching the service.
 * This is safe for the validation path since no persistence is involved.
 *
 * <p>Safaricom requires a response within 8 seconds — enforced via the configured
 * timeout chain on the UA Service HTTP client and circuit breaker.
 *
 * <p>Callback token authentication is enforced upstream by {@code CallbackTokenFilter}
 * before this controller is reached.
 *
 * @author Oualid Gharach
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "M-Pesa Validation", description = "Safaricom C2B validation callback endpoint")
public class ValidationController {

    private final ValidationService validationService;

    /**
     * Receives a Safaricom C2B validation callback.
     *
     * <p>Always returns HTTP 200 — Safaricom requires HTTP 200 for all validation
     * responses. The accept or reject decision is communicated via {@code ResultCode}
     * in the response body.
     *
     * <p>Structural validation failures (missing fields, invalid bill reference,
     * shortcode mismatch) are caught and returned as rejection responses without
     * reaching {@link ValidationService}.
     *
     * @param request the Safaricom C2B validation payload
     * @return HTTP 200 with {@link ValidationResponse} — accepted or rejected
     */
    @PostMapping("/validation")
    @Operation(
            summary = "Safaricom C2B validation callback",
            description = "Pre-payment validation check. Always returns HTTP 200 — " +
                    "accept or reject is communicated via ResultCode in the response body."
    )
    public ResponseEntity<ValidationResponse> validation(@Valid @RequestBody CallbackRequest request) {
        try {
            ValidationResponse response = validationService.validate(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Unexpected error during validation — returning OTHER_ERROR. transId={} reason={}",
                    request.transId(), e.getMessage(), e);
            return ResponseEntity.ok(ValidationResponse.rejected(
                    ValidationResultCode.OTHER_ERROR.code(),
                    ValidationResultCode.OTHER_ERROR.description()));
        }
    }

    /**
     * Handles {@link MethodArgumentNotValidException} thrown by {@code @Valid} on
     * the validation endpoint.
     *
     * <p>Returns an appropriate Safaricom rejection response based on which
     * critical field failed validation. Always HTTP 200 per Safaricom contract.
     *
     * @param ex the validation exception
     * @return HTTP 200 with rejection {@link ValidationResponse}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationResponse> handleValidationException(
            MethodArgumentNotValidException ex) {

        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();

        log.warn("Validation callback rejected — payload violations: {}",
                fieldErrors.stream()
                        .map(e -> e.getField() + ": " + e.getDefaultMessage())
                        .collect(Collectors.joining(", ")));

        ValidationResultCode code = fieldErrors.stream()
                .map(e -> switch (e.getField()) {
                    case "businessShortCode" -> ValidationResultCode.INVALID_SHORT_CODE;
                    case "billRefNumber" -> ValidationResultCode.INVALID_ACCOUNT;
                    case "transAmount" -> ValidationResultCode.INVALID_AMOUNT;
                    default -> ValidationResultCode.OTHER_ERROR;
                })
                .findFirst()
                .orElse(ValidationResultCode.OTHER_ERROR);

        return ResponseEntity.ok(ValidationResponse.rejected(code.code(), code.description()));
    }
}