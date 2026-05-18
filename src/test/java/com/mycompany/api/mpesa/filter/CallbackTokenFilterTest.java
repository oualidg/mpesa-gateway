/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/14/2026 at 9:01 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.filter;

import com.mycompany.api.mpesa.config.CallbackProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CallbackTokenFilter}.
 *
 * <p>Verifies token validation on callback endpoints and pass-through
 * behaviour on non-callback paths.
 *
 * @author Oualid Gharach
 */
@ExtendWith(MockitoExtension.class)
class CallbackTokenFilterTest {

    @Mock
    private CallbackProperties callbackProperties;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private CallbackTokenFilter callbackTokenFilter;

    private static final String VALID_TOKEN = "test-secret-token";

    // =========================================================================
    // Non-callback paths — pass through
    // =========================================================================

    @Test
    @DisplayName("Passes through requests to non-callback paths without token check")
    void shouldPassThroughNonCallbackPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        callbackTokenFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // =========================================================================
    // Valid token
    // =========================================================================

    @Test
    @DisplayName("Passes through confirmation callback with valid token")
    void shouldPassThroughConfirmationWithValidToken() throws Exception {
        when(callbackProperties.token()).thenReturn(VALID_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/confirmation");
        request.addParameter("token", VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        callbackTokenFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Passes through validation callback with valid token")
    void shouldPassThroughValidationWithValidToken() throws Exception {
        when(callbackProperties.token()).thenReturn(VALID_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/validation");
        request.addParameter("token", VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        callbackTokenFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // =========================================================================
    // Invalid token
    // =========================================================================

    @Test
    @DisplayName("Rejects confirmation callback with invalid token")
    void shouldRejectConfirmationWithInvalidToken() throws Exception {
        when(callbackProperties.token()).thenReturn(VALID_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/confirmation");
        request.addParameter("token", "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        callbackTokenFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("Rejects confirmation callback with missing token")
    void shouldRejectConfirmationWithMissingToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/confirmation");
        MockHttpServletResponse response = new MockHttpServletResponse();

        callbackTokenFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("Rejects validation callback with invalid token")
    void shouldRejectValidationWithInvalidToken() throws Exception {
        when(callbackProperties.token()).thenReturn(VALID_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/validation");
        request.addParameter("token", "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        callbackTokenFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }
}