/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/14/2026 at 10:01 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.api.mpesa.config.CallbackProperties;
import com.mycompany.api.mpesa.config.GatewayProperties;
import com.mycompany.api.mpesa.dto.CallbackRequest;
import com.mycompany.api.mpesa.dto.ValidationResponse;
import com.mycompany.api.mpesa.enums.ValidationResultCode;
import com.mycompany.api.mpesa.service.ValidationService;
import com.mycompany.api.mpesa.validation.ShortCodeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for {@link ValidationController}.
 *
 * <p>Verifies HTTP contract — always HTTP 200, correct ResultCode in response body,
 * and token validation behaviour.
 *
 * @author Oualid Gharach
 */
@WebMvcTest(ValidationController.class)
@TestPropertySource(properties = "spring.main.banner-mode=off")
class ValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ValidationService validationService;

    @MockitoBean
    private CallbackProperties callbackProperties;

    @MockitoBean
    private GatewayProperties gatewayProperties;

    @MockitoBean
    private ShortCodeValidator shortCodeValidator;

    @BeforeEach
    void setUp() {
        when(callbackProperties.token()).thenReturn("test-token");
        when(gatewayProperties.shortCode()).thenReturn("600123");
    }

    // =========================================================================
    // HTTP 200 — accepted
    // =========================================================================

    @Test
    @DisplayName("Returns HTTP 200 with accepted ResultCode when validation passes")
    void shouldReturn200WithAcceptedResultCode() throws Exception {
        when(validationService.validate(any())).thenReturn(ValidationResponse.accepted());

        mockMvc.perform(post("/api/v1/validation")
                        .param("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResultCode").value(ValidationResultCode.ACCEPTED.code()))
                .andExpect(jsonPath("$.ResultDesc").value(ValidationResultCode.ACCEPTED.description()));
    }

    // =========================================================================
    // HTTP 200 — rejected
    // =========================================================================

    @Test
    @DisplayName("Returns HTTP 200 with rejection ResultCode when validation fails")
    void shouldReturn200WithRejectionResultCode() throws Exception {
        when(validationService.validate(any())).thenReturn(
                ValidationResponse.rejected(
                        ValidationResultCode.INVALID_ACCOUNT.code(),
                        ValidationResultCode.INVALID_ACCOUNT.description()));

        mockMvc.perform(post("/api/v1/validation")
                        .param("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResultCode").value(ValidationResultCode.INVALID_ACCOUNT.code()));
    }

    // =========================================================================
    // HTTP 200 — invalid payload handled by exception handler
    // =========================================================================

    @Test
    @DisplayName("Returns HTTP 200 with OTHER_ERROR when transId is missing")
    void shouldReturn200WithOtherErrorWhenTransIdMissing() throws Exception {
        CallbackRequest invalidRequest = new CallbackRequest(
                "Pay Bill", null, "20240315123045",
                new BigDecimal("1500.00"), "600123", "1234567897",
                null, null, null, "254712345678", null, null, null
        );

        mockMvc.perform(post("/api/v1/validation")
                        .param("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResultCode").value(ValidationResultCode.OTHER_ERROR.code()));
    }

    // =========================================================================
    // HTTP 401 — missing or invalid token
    // =========================================================================

    @Test
    @DisplayName("Returns HTTP 401 when callback token is missing")
    void shouldReturn401WhenTokenIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Returns HTTP 401 when callback token is invalid")
    void shouldReturn401WhenTokenIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/validation")
                        .param("token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private CallbackRequest validRequest() {
        return new CallbackRequest(
                "Pay Bill", "NLJ7RT61SV", "20240315123045",
                new BigDecimal("1500.00"), "600123", "1234567897",
                null, null, null, "254712345678", "John", null, "Doe"
        );
    }
}