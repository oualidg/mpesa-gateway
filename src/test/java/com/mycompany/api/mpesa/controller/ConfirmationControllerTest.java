/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/14/2026 at 10:00 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.api.mpesa.config.CallbackProperties;
import com.mycompany.api.mpesa.dto.CallbackRequest;
import com.mycompany.api.mpesa.service.ConfirmationService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for {@link ConfirmationController}.
 *
 * <p>Verifies HTTP contract — status codes and delegation to {@link ConfirmationService}.
 * Filters are included to test token validation behaviour.
 *
 * @author Oualid Gharach
 */
@WebMvcTest(ConfirmationController.class)
@TestPropertySource(properties = "spring.main.banner-mode=off")
class ConfirmationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ConfirmationService confirmationService;

    @MockitoBean
    private CallbackProperties callbackProperties;

    // =========================================================================
    // HTTP 200 — success
    // =========================================================================

    @Test
    @DisplayName("Returns HTTP 200 when confirmation is ingested successfully")
    void shouldReturn200WhenConfirmationIngestedSuccessfully() throws Exception {
        org.mockito.Mockito.when(callbackProperties.token()).thenReturn("test-token");

        mockMvc.perform(post("/api/v1/confirmation")
                        .param("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk());

        verify(confirmationService).ingest(any(CallbackRequest.class));
    }

    // =========================================================================
    // HTTP 200 — invalid payload still persisted
    // =========================================================================

    @Test
    @DisplayName("Returns HTTP 200 even when payload is invalid — service handles suspension")
    void shouldReturn200EvenForInvalidPayload() throws Exception {
        org.mockito.Mockito.when(callbackProperties.token()).thenReturn("test-token");

        CallbackRequest invalidRequest = new CallbackRequest(
                null, null, null, null, null, null,
                null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/api/v1/confirmation")
                        .param("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // HTTP 500 — MongoDB failure
    // =========================================================================

    @Test
    @DisplayName("Returns HTTP 500 when MongoDB transaction fails so Safaricom retries")
    void shouldReturn500WhenMongoTransactionFails() throws Exception {
        org.mockito.Mockito.when(callbackProperties.token()).thenReturn("test-token");
        doThrow(new RuntimeException("MongoDB failure"))
                .when(confirmationService).ingest(any());

        mockMvc.perform(post("/api/v1/confirmation")
                        .param("token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isInternalServerError());
    }

    // =========================================================================
    // HTTP 401 — missing or invalid token
    // =========================================================================

    @Test
    @DisplayName("Returns HTTP 401 when callback token is missing")
    void shouldReturn401WhenTokenIsMissing() throws Exception {
        org.mockito.Mockito.when(callbackProperties.token()).thenReturn("test-token");

        mockMvc.perform(post("/api/v1/confirmation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Returns HTTP 401 when callback token is invalid")
    void shouldReturn401WhenTokenIsInvalid() throws Exception {
        org.mockito.Mockito.when(callbackProperties.token()).thenReturn("test-token");

        mockMvc.perform(post("/api/v1/confirmation")
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