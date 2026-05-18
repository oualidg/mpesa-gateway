/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 1:37 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.filter;

import com.mycompany.api.mpesa.config.CallbackProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that enforces callback token authentication on Safaricom C2B endpoints.
 *
 * <p>Safaricom callback endpoints include a secret token as a query parameter:
 * <ul>
 *   <li>{@code POST /mpesa/validation?token={secret}}</li>
 *   <li>{@code POST /mpesa/confirmation?token={secret}}</li>
 * </ul>
 *
 * <p>Incoming requests to these endpoints must include the correct token value.
 * Requests with a missing or invalid token are rejected with HTTP 401 before
 * reaching any controller. All other paths are passed through without inspection.
 *
 * <p>This filter runs at highest precedence to ensure token validation occurs
 * before any other filter or handler processes the request.
 *
 * <p>This is a secondary security control — IP allowlisting is the primary
 * control and is enforced at the Nginx layer.
 *
 * @author Oualid Gharach
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CallbackTokenFilter extends OncePerRequestFilter {

    private static final String TOKEN_PARAM = "token";
    private static final String VALIDATION_PATH = "/api/v1/validation";
    private static final String CONFIRMATION_PATH = "/api/v1/confirmation";

    private final CallbackProperties callbackProperties;

    /**
     * Validates the callback token on Safaricom C2B endpoints.
     *
     * <p>Passes all non-callback paths through without inspection.
     * Rejects requests to callback paths with a missing or invalid token
     * with HTTP 401.
     *
     * @param request     the inbound HTTP request
     * @param response    the HTTP response
     * @param filterChain the filter chain
     * @throws ServletException if filter chain processing fails
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!isCallbackPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getParameter(TOKEN_PARAM);

        if (token == null || !token.equals(callbackProperties.token())) {
            log.warn("Rejected callback request — missing or invalid token. path={} remoteAddr={}",
                    path, request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Returns {@code true} if the request path is a Safaricom callback endpoint.
     *
     * @param path the request URI
     * @return {@code true} if the path requires token validation
     */
    private boolean isCallbackPath(String path) {
        return path.startsWith(VALIDATION_PATH) || path.startsWith(CONFIRMATION_PATH);
    }
}