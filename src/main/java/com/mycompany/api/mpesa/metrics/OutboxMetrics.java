/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/19/2026 at 1:06 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.metrics;

import com.mycompany.api.mpesa.enums.OutboxStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Micrometer gauge metrics for the outbox collection.
 *
 * <p>Tracks two operational signals:
 * <ul>
 *   <li>{@code mpesa.outbox.pending} — current count of {@code PENDING} outbox
 *       entries. A sustained non-zero value indicates the outbox processor is
 *       not draining — broker unavailability or persistent publish failures.</li>
 *   <li>{@code mpesa.outbox.oldest_age_seconds} — age in seconds of the oldest
 *       {@code PENDING} entry. Feeds the 5-minute alert threshold defined in the
 *       architecture spec. Returns {@code 0} when no pending entries exist.</li>
 * </ul>
 *
 * <p>Gauges are registered once at construction time against live
 * {@link MongoTemplate} query lambdas. Micrometer polls them on each scrape —
 * no application-level scheduling is required.
 *
 * <p>This bean must be Spring-managed so Micrometer's weak reference to the
 * gauge supplier does not get garbage collected.
 *
 * <p>No {@code @RequiredArgsConstructor} — the constructor performs gauge
 * registration logic beyond simple field assignment.
 *
 * @author Oualid Gharach
 */
@Slf4j
@Component
public class OutboxMetrics {

    private static final String OUTBOX_PENDING    = "mpesa.outbox.pending";
    private static final String OUTBOX_OLDEST_AGE = "mpesa.outbox.oldest_age_seconds";

    private final MongoTemplate mongoTemplate;

    /**
     * Constructs and registers outbox gauges with the provided {@link MeterRegistry}.
     *
     * @param mongoTemplate the MongoDB template used for live gauge queries
     * @param registry      the Micrometer registry
     */
    public OutboxMetrics(MongoTemplate mongoTemplate, MeterRegistry registry) {
        this.mongoTemplate = mongoTemplate;

        Gauge.builder(OUTBOX_PENDING, this, OutboxMetrics::pendingCount)
                .description("Number of outbox entries in PENDING state awaiting publication")
                .register(registry);

        Gauge.builder(OUTBOX_OLDEST_AGE, this, OutboxMetrics::oldestPendingAgeSeconds)
                .description("Age in seconds of the oldest PENDING outbox entry")
                .register(registry);
    }

    // =========================================================================
    // Gauge suppliers
    // =========================================================================

    /**
     * Returns the current count of {@code PENDING} outbox entries.
     *
     * @return count of pending entries, or {@code 0} if none
     */
    private double pendingCount() {
        Query query = new Query(Criteria.where("status").is(OutboxStatus.PENDING));
        return mongoTemplate.count(query, "outbox_entries");
    }

    /**
     * Returns the age in seconds of the oldest {@code PENDING} outbox entry.
     *
     * <p>Returns {@code 0} when no pending entries exist — allows Prometheus
     * alert rules to threshold on age without special-casing the empty state.
     *
     * @return age in seconds of the oldest pending entry, or {@code 0} if none
     */
    private double oldestPendingAgeSeconds() {
        Query query = new Query(Criteria.where("status").is(OutboxStatus.PENDING))
                .limit(1);
        query.fields().include("createdAt");

        var entry = mongoTemplate.findOne(query, org.bson.Document.class, "outbox_entries");
        if (entry == null) {
            return 0;
        }

        Object createdAt = entry.get("createdAt");
        if (!(createdAt instanceof java.util.Date date)) {
            return 0;
        }

        return (Instant.now().toEpochMilli() - date.getTime()) / 1000.0;
    }
}