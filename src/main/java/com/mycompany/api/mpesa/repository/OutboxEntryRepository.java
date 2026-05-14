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

import com.mycompany.api.mpesa.document.OutboxEntry;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data MongoDB repository for {@link OutboxEntry} documents.
 *
 * <p>Provides standard CRUD operations inherited from {@link MongoRepository},
 * plus a query for the outbox processor polling loop.
 *
 * <p>The atomic claim operation (findAndModify) required by the outbox processor
 * is not expressible via Spring Data derived queries — it is implemented directly
 * using {@code MongoTemplate} in the outbox processor component.
 *
 * <p>Auto-index creation is disabled — indexes are managed programmatically
 * via {@code MongoIndexConfig} on startup.
 *
 * @author Oualid Gharach
 */
@Repository
public interface OutboxEntryRepository extends MongoRepository<OutboxEntry, ObjectId> {

    /**
     * Returns a page of unsent outbox entries ordered by creation time ascending.
     *
     * <p>Used by the outbox processor to drain the backlog in creation order.
     * Pagination is applied via {@link Pageable} to bound the batch size per poll cycle.
     *
     * @param sent     the sent flag to filter by (expected: {@code false})
     * @param pageable pagination and sort — caller supplies batch size and sort order
     * @return unsent entries within the requested page
     */
    List<OutboxEntry> findBySent(boolean sent, Pageable pageable);
}