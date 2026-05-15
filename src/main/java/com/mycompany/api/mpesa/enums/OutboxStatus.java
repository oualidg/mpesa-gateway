/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/15/2026 at 3:25 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.enums;

/**
 * Lifecycle status of an {@link com.mycompany.api.mpesa.document.OutboxEntry}.
 *
 * <p>State transitions:
 * <ul>
 *   <li>{@code PENDING} → {@code PROCESSING} — atomic claim by outbox processor</li>
 *   <li>{@code PROCESSING} → {@code SENT} — successful RabbitMQ publish</li>
 *   <li>{@code PROCESSING} → {@code PENDING} — publish failure or stale lease (crash recovery)</li>
 *   <li>{@code PROCESSING} → {@code FAILED} — permanent failure (missing event)</li>
 * </ul>
 *
 * @author Oualid Gharach
 */
public enum OutboxStatus {

    /** Waiting to be claimed by the outbox processor. */
    PENDING,

    /**
     * Claimed by the outbox processor — lease timeout protects against permanent
     * lock if the JVM crashes before publish completes.
     */
    PROCESSING,

    /** Successfully published to RabbitMQ and broker-acknowledged. */
    SENT,

    /**
     * Permanent failure — referenced event is missing or unrecoverable.
     * Will not be retried automatically. Requires operator investigation.
     */
    FAILED
}