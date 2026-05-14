/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 2:15 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mycompany.api.mpesa.validation.ValidBillRef;
import com.mycompany.api.mpesa.validation.ValidShortCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Inbound HTTP request DTO representing a Safaricom C2B callback payload.
 *
 * <p>Used for both validation and confirmation callbacks — Safaricom sends
 * the same payload structure for both. All 13 fields from the Safaricom
 * contract are included; optional fields are nullable.
 *
 * <p>Field names are mapped from Safaricom's PascalCase convention to this
 * record's camelCase fields via {@code @JsonProperty}.
 *
 * <p>Jakarta validation annotations are present on critical fields only —
 * those required for payment processing. Non-critical fields ({@code transactionType},
 * {@code transTime}, {@code msisdn}, name fields, optional references) carry no
 * annotations and are stored as-is.
 *
 * <p><strong>DO NOT add validation annotations to non-critical fields.</strong>
 * Missing or malformed non-critical values must never cause a payment to be
 * rejected or suspended.
 *
 * <p>Validation is invoked manually via {@code Validator.validate(request)} —
 * never via {@code @Valid} at the controller — to ensure each flow handles
 * violations appropriately:
 * <ul>
 *   <li>Validation flow — reject immediately with Safaricom error response</li>
 *   <li>Confirmation flow — validates mapped {@link com.mycompany.api.mpesa.document.MpesaEvent}
 *       after trimming and normalisation, persists as {@code SUSPENDED} on violation</li>
 * </ul>
 *
 * @param transactionType    Safaricom transaction type (e.g. "Pay Bill")
 * @param transId            Safaricom unique transaction reference
 * @param transTime          Safaricom transaction timestamp (format: YYYYMMDDHHmmss)
 * @param transAmount        amount paid by the subscriber
 * @param businessShortCode  paybill number — must match the configured shortcode
 * @param billRefNumber      user-entered reference identifying the account
 * @param invoiceNumber      optional invoice reference
 * @param orgAccountBalance  optional organisation account balance
 * @param thirdPartyTransId  optional external transaction reference
 * @param msisdn             subscriber phone number
 * @param firstName          subscriber first name (optional)
 * @param middleName         subscriber middle name (optional)
 * @param lastName           subscriber last name (optional)
 *
 * @author Oualid Gharach
 */
public record CallbackRequest(

        // =====================================================================
        // Non-critical — stored as-is. DO NOT add validation annotations.
        // =====================================================================

        @JsonProperty("TransactionType")
        String transactionType,

        // =====================================================================
        // Critical — required for payment processing and idempotency.
        // =====================================================================

        @NotBlank(message = "TransID is required")
        @JsonProperty("TransID")
        String transId,

        // =====================================================================
        // Non-critical — stored as-is for reconciliation. DO NOT add validation.
        // =====================================================================

        @JsonProperty("TransTime")
        String transTime,

        // =====================================================================
        // Critical
        // =====================================================================

        @NotNull(message = "TransAmount is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "TransAmount must be greater than zero")
        @JsonProperty("TransAmount")
        BigDecimal transAmount,

        @NotBlank(message = "BusinessShortCode is required")
        @ValidShortCode
        @JsonProperty("BusinessShortCode")
        String businessShortCode,

        @NotBlank(message = "BillRefNumber is required")
        @ValidBillRef
        @JsonProperty("BillRefNumber")
        String billRefNumber,

        // =====================================================================
        // Non-critical — stored as-is. DO NOT add validation annotations.
        // =====================================================================

        @JsonProperty("InvoiceNumber")
        String invoiceNumber,

        @JsonProperty("OrgAccountBalance")
        BigDecimal orgAccountBalance,

        @JsonProperty("ThirdPartyTransID")
        String thirdPartyTransId,

        @JsonProperty("MSISDN")
        String msisdn,

        @JsonProperty("FirstName")
        String firstName,

        @JsonProperty("MiddleName")
        String middleName,

        @JsonProperty("LastName")
        String lastName

) {}