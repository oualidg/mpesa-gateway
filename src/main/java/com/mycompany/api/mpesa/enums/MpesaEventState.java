/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 1:09 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.enums;

/**
 * Represents the lifecycle states of a {@code MpesaEvent}.
 *
 * <p>Valid transitions:
 * <ul>
 *   <li>{@code RECEIVED} → {@code POSTED} — payment successfully posted to the UA Service</li>
 *   <li>{@code RECEIVED} → {@code SUSPENDED} — validation failure or retry exhaustion</li>
 * </ul>
 *
 * <p>Invariants:
 * <ul>
 *   <li>{@code POSTED} must have a {@code billingReceipt}</li>
 *   <li>{@code SUSPENDED} must have a {@code failureReason}</li>
 *   <li>{@code RECEIVED} must have neither</li>
 * </ul>
 */
public enum MpesaEventState {

    /** Event stored, awaiting asynchronous processing. Non-terminal. */
    RECEIVED,

    /** Payment successfully posted to the UA Service. Terminal. */
    POSTED,

    /** Processing failed — requires operator investigation. Terminal. */
    SUSPENDED
}