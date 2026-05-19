/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/19/2026 at 1:01 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Micrometer metrics for inbound Safaricom C2B confirmation callbacks.
 *
 * <p>Tracks two signals:
 * <ul>
 *   <li>{@code mpesa.callbacks.received} — counts inbound callbacks by outcome
 *       ({@code success} or {@code failure})</li>
 *   <li>{@code mpesa.callbacks.latency} — measures end-to-end ingest duration
 *       from controller entry to persistence return</li>
 * </ul>
 *
 * <p>Counters are pre-built at construction time — one per outcome tag value —
 * to avoid per-request registry lookups.
 *
 * @author Oualid Gharach
 */
@Component
public class MpesaCallbackMetrics {

    private static final String CALLBACKS_RECEIVED = "mpesa.callbacks.received";
    private static final String CALLBACKS_LATENCY  = "mpesa.callbacks.latency";
    private static final String TAG_RESULT         = "result";
    private static final String RESULT_SUCCESS     = "success";
    private static final String RESULT_FAILURE     = "failure";

    private final Counter successCounter;
    private final Counter failureCounter;
    private final Timer   latencyTimer;

    /**
     * Constructs and registers all meters with the provided {@link MeterRegistry}.
     *
     * @param registry the Micrometer registry
     */
    public MpesaCallbackMetrics(MeterRegistry registry) {
        this.successCounter = Counter.builder(CALLBACKS_RECEIVED)
                .description("Number of inbound Safaricom C2B confirmation callbacks")
                .tag(TAG_RESULT, RESULT_SUCCESS)
                .register(registry);

        this.failureCounter = Counter.builder(CALLBACKS_RECEIVED)
                .description("Number of inbound Safaricom C2B confirmation callbacks")
                .tag(TAG_RESULT, RESULT_FAILURE)
                .register(registry);

        this.latencyTimer = Timer.builder(CALLBACKS_LATENCY)
                .description("End-to-end duration of confirmation callback ingest")
                .register(registry);
    }

    /**
     * Records a successful callback ingest — event persisted to MongoDB.
     */
    public void recordSuccess() {
        successCounter.increment();
    }

    /**
     * Records a failed callback ingest — MongoDB persistence failed entirely.
     */
    public void recordFailure() {
        failureCounter.increment();
    }

    /**
     * Returns the latency timer for wrapping the full ingest call.
     *
     * @return the ingest latency timer
     */
    public Timer latencyTimer() {
        return latencyTimer;
    }
}