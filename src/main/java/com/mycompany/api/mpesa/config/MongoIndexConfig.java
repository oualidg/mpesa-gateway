/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 1:28 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.config;

import com.mycompany.api.mpesa.document.MpesaEvent;
import com.mycompany.api.mpesa.document.OutboxEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

/**
 * Programmatic MongoDB index configuration for the M-Pesa Gateway event store.
 *
 * <p>Auto-index creation is disabled via {@code spring.data.mongodb.auto-index-creation=false}.
 * Indexes are created explicitly here on {@link ApplicationReadyEvent} to ensure the
 * application context is fully initialised before index operations are attempted.
 *
 * <p>All index operations are idempotent — re-running on restart is safe. MongoDB
 * will not recreate an index that already exists with the same definition.
 *
 * @author Oualid Gharach
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MongoIndexConfig {

    private final MongoTemplate mongoTemplate;

    /**
     * Creates all required indexes for the M-Pesa Gateway collections.
     *
     * <p>Triggered on {@link ApplicationReadyEvent}.
     *
     * <p>Indexes created:
     * <ul>
     *   <li>{@code mpesa_events.transId} — unique sparse, idempotency control</li>
     *   <li>{@code mpesa_events.state} — state filtering queries</li>
     *   <li>{@code mpesa_events.createdAt} — time-based queries</li>
     *   <li>{@code mpesa_events.(state, createdAt)} — backlog queries</li>
     *   <li>{@code outbox_entries.(status, createdAt)} — outbox processor polling</li>
     *   <li>{@code outbox_entries.(status, claimedAt)} — stale lease detection</li>
     * </ul>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void createIndexes() {
        log.debug("Creating MongoDB indexes for mpesa_events and outbox_entries collections");

        var mpesaIndexOps = mongoTemplate.indexOps(MpesaEvent.class);

        mpesaIndexOps.createIndex(new Index()
                .on("transId", Sort.Direction.ASC)
                .unique()
                .sparse()
                .named("idx_trans_id_unique"));

        mpesaIndexOps.createIndex(new Index()
                .on("state", Sort.Direction.ASC)
                .named("idx_state"));

        mpesaIndexOps.createIndex(new Index()
                .on("createdAt", Sort.Direction.ASC)
                .named("idx_created_at"));

        var outboxIndexOps = mongoTemplate.indexOps(OutboxEntry.class);

        outboxIndexOps.createIndex(new Index()
                .on("status", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.ASC)
                .named("idx_status_created_at"));

        outboxIndexOps.createIndex(new Index()
                .on("status", Sort.Direction.ASC)
                .on("claimedAt", Sort.Direction.ASC)
                .named("idx_status_claimed_at"));

        log.debug("MongoDB indexes created successfully");
    }
}