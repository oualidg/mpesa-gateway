/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/19/2026 at 1:09 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.metrics;

import com.mycompany.api.mpesa.enums.MpesaEventState;
import com.mycompany.api.mpesa.repository.MpesaEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Micrometer metrics for {@link com.mycompany.api.mpesa.document.MpesaEvent} state.
 *
 * <p>Tracks two signals:
 * <ul>
 *   <li>{@code mpesa.events.state} — live gauge per state ({@code RECEIVED},
 *       {@code POSTED}, {@code SUSPENDED}), queried from MongoDB on each Prometheus
 *       scrape. Provides a current snapshot of the event lifecycle distribution.</li>
 *   <li>{@code mpesa.events.transitions} — cumulative counter per terminal state
 *       ({@code POSTED}, {@code SUSPENDED}), incremented at the moment of transition
 *       in {@link com.mycompany.api.mpesa.service.ResultProcessingService}. Provides
 *       a rate signal for alerting on SUSPENDED spikes.</li>
 * </ul>
 *
 * <p>Gauges are registered once at construction time against
 * {@link MpesaEventRepository} query lambdas. Micrometer polls them on each scrape.
 *
 * <p>Transition counters are pre-built at construction time — one per terminal
 * state — to avoid per-call registry lookups.
 *
 * <p>No {@code @RequiredArgsConstructor} — the constructor performs gauge and
 * counter registration logic beyond simple field assignment.
 *
 * @author Oualid Gharach
 */
@Component
public class EventStateMetrics {

    private static final String EVENTS_STATE       = "mpesa.events.state";
    private static final String EVENTS_TRANSITIONS = "mpesa.events.transitions";
    private static final String TAG_STATE          = "state";

    private final MpesaEventRepository mpesaEventRepository;

    private final Counter postedCounter;
    private final Counter suspendedCounter;

    /**
     * Constructs and registers all event state meters with the provided {@link MeterRegistry}.
     *
     * @param mpesaEventRepository the repository used for live gauge queries
     * @param registry             the Micrometer registry
     */
    public EventStateMetrics(MpesaEventRepository mpesaEventRepository, MeterRegistry registry) {
        this.mpesaEventRepository = mpesaEventRepository;

        for (MpesaEventState state : MpesaEventState.values()) {
            Gauge.builder(EVENTS_STATE, this, m -> m.countByState(state))
                    .description("Current number of MpesaEvent documents in each state")
                    .tag(TAG_STATE, state.name())
                    .register(registry);
        }

        this.postedCounter = Counter.builder(EVENTS_TRANSITIONS)
                .description("Cumulative number of MpesaEvent terminal state transitions")
                .tag(TAG_STATE, MpesaEventState.POSTED.name())
                .register(registry);

        this.suspendedCounter = Counter.builder(EVENTS_TRANSITIONS)
                .description("Cumulative number of MpesaEvent terminal state transitions")
                .tag(TAG_STATE, MpesaEventState.SUSPENDED.name())
                .register(registry);
    }

    // =========================================================================
    // Transition recording — called by ResultProcessingService
    // =========================================================================

    /**
     * Records a terminal state transition to {@code POSTED}.
     *
     * <p>Called by {@link com.mycompany.api.mpesa.service.ResultProcessingService}
     * after successful persistence of the POSTED state.
     */
    public void recordPosted() {
        postedCounter.increment();
    }

    /**
     * Records a terminal state transition to {@code SUSPENDED}.
     *
     * <p>Called by {@link com.mycompany.api.mpesa.service.ResultProcessingService}
     * after successful persistence of the SUSPENDED state.
     */
    public void recordSuspended() {
        suspendedCounter.increment();
    }

    // =========================================================================
    // Gauge supplier
    // =========================================================================

    /**
     * Returns the current count of {@link com.mycompany.api.mpesa.document.MpesaEvent}
     * documents in the given state.
     *
     * @param state the event state to count
     * @return document count for the given state
     */
    private double countByState(MpesaEventState state) {
        return mpesaEventRepository.countByState(state);
    }
}