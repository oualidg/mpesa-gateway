/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 1:17 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.document;

import com.mycompany.api.mpesa.enums.OutboxStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document representing a guaranteed publication intent for a {@link MpesaEvent}.
 *
 * <p>Created atomically alongside {@link MpesaEvent} during confirmation ingest.
 * Stores publication intent only — all business data remains in {@link MpesaEvent}.
 *
 * <p>Lifecycle is managed by the outbox processor:
 * <ul>
 *   <li>Created as {@code PENDING}</li>
 *   <li>Claimed atomically — transitions to {@code PROCESSING} with {@code claimedAt}</li>
 *   <li>On successful publish — transitions to {@code SENT}, {@code claimedAt} cleared</li>
 *   <li>On publish failure — reset to {@code PENDING} with {@code attemptCount} incremented
 *       and {@code lastError} recorded</li>
 *   <li>On stale lease (JVM crash) — reset to {@code PENDING} by lease timeout check</li>
 *   <li>On permanent failure (missing event) — transitions to {@code FAILED},
 *       {@code claimedAt} cleared</li>
 * </ul>
 *
 * <p>{@code @Data} is not used — {@code equals}/{@code hashCode} are based on
 * {@code id} only.
 *
 * @author Oualid Gharach
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "outbox_entries")
@CompoundIndex(name = "idx_status_created_at", def = "{'status': 1, 'createdAt': 1}")
public class OutboxEntry {

    @Id
    private ObjectId id;

    /** Reference to the corresponding {@link MpesaEvent#getId()}. */
    private ObjectId eventId;

    /** Current processing status. */
    private OutboxStatus status;

    /** Number of publish attempts — incremented on each transient failure for operational visibility. */
    private int attemptCount;

    /** Last error message — set on publish failure for operational debugging. */
    private String lastError;

    private Instant createdAt;

    /** Set when the entry is claimed by the outbox processor. Cleared on terminal transitions. */
    private Instant claimedAt;

    /** Set only after successful publish call. */
    private Instant publishedAt;

    /**
     * Creates a new outbox entry in {@code PENDING} state for the given event.
     *
     * @param eventId the ID of the corresponding {@link MpesaEvent}
     * @return a new {@link OutboxEntry} ready for processing
     */
    public static OutboxEntry forEvent(ObjectId eventId) {
        OutboxEntry entry = new OutboxEntry();
        entry.setEventId(eventId);
        entry.setStatus(OutboxStatus.PENDING);
        entry.setAttemptCount(0);
        entry.setCreatedAt(Instant.now());
        return entry;
    }

    // =========================================================================
    // equals / hashCode — based on id only
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutboxEntry that = (OutboxEntry) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}