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
 * <p>{@code @Data} is not used — {@code equals}/{@code hashCode} are based on
 * {@code id} only.
 *
 * @author Oualid Gharach
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "outbox_entries")
@CompoundIndex(name = "idx_sent_created_at", def = "{'sent': 1, 'createdAt': 1}")
public class OutboxEntry {

    @Id
    private ObjectId id;

    /** Reference to the corresponding {@link MpesaEvent#getId()}. */
    private ObjectId eventId;

    /** {@code true} only after the provisioning message is broker-acknowledged. Primitive — never null. */
    private boolean sent;

    private Instant createdAt;
    private Instant publishedAt;

    /**
     * Creates an unsent outbox entry for the given event.
     *
     * @param eventId the ID of the corresponding {@link MpesaEvent}
     * @return a new unsent {@link OutboxEntry}
     */
    public static OutboxEntry forEvent(ObjectId eventId) {
        OutboxEntry entry = new OutboxEntry();
        entry.setEventId(eventId);
        entry.setSent(false);
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