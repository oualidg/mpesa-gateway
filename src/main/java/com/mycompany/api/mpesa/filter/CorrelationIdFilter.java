/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 2:11 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that establishes a correlation ID for every inbound request.
 *
 * <p>The correlation ID is sourced from the {@code X-Correlation-ID} request header,
 * which Nginx sets via {@code $request_id} for external traffic. If the header is
 * absent — for example in local development, tests, or internal calls — a UUID is
 * generated as a fallback.
 *
 * <p>The correlation ID is placed in MDC under the key {@code correlationId} and
 * propagated on all outbound HTTP calls via the {@code X-Correlation-ID} header.
 * MDC is cleared in a {@code finally} block after the request completes to prevent
 * context leakage across thread pool reuse.
 *
 * <p>This filter runs at second-highest precedence — immediately after
 * {@link CallbackTokenFilter} — so that all subsequent filters and handlers
 * have access to the correlation ID in MDC.
 *
 * @author Oualid Gharach
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_CORRELATION_ID = "correlationId";

    /**
     * Extracts or generates a correlation ID and places it in MDC for the
     * duration of the request.
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
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            log.info("No correlation ID in request — generated fallback. correlationId={}", correlationId);
        }

        MDC.put(MDC_CORRELATION_ID, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }
}