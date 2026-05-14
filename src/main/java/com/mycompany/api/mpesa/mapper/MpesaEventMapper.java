/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 5:40 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.mapper;

import com.mycompany.api.mpesa.document.MpesaEvent;
import com.mycompany.api.mpesa.dto.CallbackRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * MapStruct mapper for converting a {@link CallbackRequest} into a {@link MpesaEvent}.
 *
 * <p>Mapping always succeeds — null fields stay null and no exceptions are thrown
 * during conversion. All string fields are trimmed to normalise leading and trailing
 * whitespace from external Safaricom payloads. {@code transId} is additionally
 * normalised to null if blank, ensuring consistent sparse index behaviour.
 *
 * <p>{@code transTime} is mapped as a raw string — no parsing is applied, preserving
 * the original Safaricom value regardless of format changes.
 *
 * <p>Business validation is applied in the confirmation service after mapping via
 * {@code Validator.validate(event)} — the mapper is a pure structural converter
 * with no business logic.
 *
 * <p>Fields not present on {@link CallbackRequest} — {@code state},
 * {@code createdAt}, {@code billingReceipt}, {@code failureReason}, {@code postedAt},
 * {@code updatedAt} — are set by the confirmation service after mapping, never here.
 *
 * @author Oualid Gharach
 */
@Mapper(componentModel = "spring")
public interface MpesaEventMapper {

    /**
     * Maps a {@link CallbackRequest} to a {@link MpesaEvent}.
     *
     * <p>All string fields are trimmed. {@code transId} empty values are normalised
     * to null by the confirmation service after mapping. Lifecycle fields are ignored —
     * initialised by the confirmation service.
     *
     * @param request the inbound Safaricom callback payload
     * @return a new {@link MpesaEvent} with fields mapped and trimmed from the request
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "billingReceipt", ignore = true)
    @Mapping(target = "failureReason", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "postedAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "transId", source = "transId", qualifiedByName = "trimToNull")
    @Mapping(target = "transactionType", source = "transactionType", qualifiedByName = "trim")
    @Mapping(target = "businessShortCode", source = "businessShortCode", qualifiedByName = "trim")
    @Mapping(target = "billRefNumber", source = "billRefNumber", qualifiedByName = "trim")
    @Mapping(target = "invoiceNumber", source = "invoiceNumber", qualifiedByName = "trim")
    @Mapping(target = "thirdPartyTransId", source = "thirdPartyTransId", qualifiedByName = "trim")
    @Mapping(target = "msisdn", source = "msisdn", qualifiedByName = "trim")
    @Mapping(target = "firstName", source = "firstName", qualifiedByName = "trim")
    @Mapping(target = "middleName", source = "middleName", qualifiedByName = "trim")
    @Mapping(target = "lastName", source = "lastName", qualifiedByName = "trim")
    @Mapping(target = "amount", source = "transAmount")
    @Mapping(target = "transTime", source = "transTime", qualifiedByName = "trim")
    MpesaEvent toMpesaEvent(CallbackRequest request);

    /**
     * Trims leading and trailing whitespace from a string value.
     *
     * <p>Returns null if the input is null — null fields are preserved as-is
     * and not converted to empty strings.
     *
     * @param value the string to trim
     * @return trimmed string, or null if input is null
     */
    @Named("trim")
    default String trim(String value) {
        return value != null ? value.trim() : null;
    }

    /**
     * Trims leading and trailing whitespace from a string value and returns
     * null if the result is empty.
     *
     * <p>Used for {@code transId} only — ensures empty or whitespace-only values
     * become null for consistent sparse index behaviour.
     *
     * @param value the string to trim
     * @return trimmed string, null if input is null or blank
     */
    @Named("trimToNull")
    default String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}