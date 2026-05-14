/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 1:23 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.repository;

import com.mycompany.api.mpesa.document.MpesaEvent;
import com.mycompany.api.mpesa.enums.MpesaEventState;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * Spring Data MongoDB repository for {@link MpesaEvent} documents.
 *
 * <p>Provides standard CRUD operations inherited from {@link MongoRepository},
 * plus a custom query for the validation fallback (ADR-006).
 *
 * <p>Idempotency on confirmation ingest is enforced via the unique index on
 * {@code transId} using an insert-first strategy — no pre-check query is performed.
 *
 * <p>Auto-index creation is disabled — indexes are managed programmatically
 * via {@code MongoIndexConfig} on startup.
 *
 * @author Oualid Gharach
 */
@Repository
public interface MpesaEventRepository extends MongoRepository<MpesaEvent, ObjectId> {

    /**
     * Checks whether a successfully posted event exists for the given bill reference
     * within a time window.
     *
     * <p>Used by the validation fallback (ADR-006) when the UA Service circuit breaker
     * is open. If a reference has been successfully posted within the configured window,
     * it is treated as valid for validation purposes.
     *
     * @param billRefNumber the normalised bill reference
     * @param state         the event state to filter by (expected: {@code POSTED})
     * @param since         the start of the time window
     * @return {@code true} if a matching event exists within the window
     */
    boolean existsByBillRefNumberAndStateAndCreatedAtAfter(
            String billRefNumber,
            MpesaEventState state,
            Instant since);
}