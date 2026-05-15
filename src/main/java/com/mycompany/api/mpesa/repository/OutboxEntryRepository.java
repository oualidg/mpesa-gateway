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
import com.mycompany.api.mpesa.enums.OutboxStatus;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for {@link OutboxEntry} documents.
 *
 * <p>Provides standard CRUD operations inherited from {@link MongoRepository}.
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
}