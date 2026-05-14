/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 8:58 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.enums;

/**
 * Safaricom C2B validation result codes used in validation callback responses.
 *
 * <p>These codes are defined by the Safaricom Daraja API contract and are fixed
 * protocol constants — they are not environment-specific and belong in code
 * rather than configuration.
 *
 * <p>A result code of {@code "0"} indicates acceptance. All other codes indicate
 * rejection with a specific reason communicated to Safaricom.
 *
 * <p>Reference: Safaricom Daraja C2B API documentation.
 *
 * @author Oualid Gharach
 */
public enum ValidationResultCode {

    /** Transaction accepted. */
    ACCEPTED("0", "Accepted"),

    /** Invalid MSISDN. */
    INVALID_MSISDN("C2B00011", "Invalid MSISDN"),

    /** Invalid account number. */
    INVALID_ACCOUNT("C2B00012", "Invalid Account Number"),

    /** Invalid amount. */
    INVALID_AMOUNT("C2B00013", "Invalid Amount"),

    /** Invalid KYC details. */
    INVALID_KYC("C2B00014", "Invalid KYC Details"),

    /** Invalid short code. */
    INVALID_SHORT_CODE("C2B00015", "Invalid Short Code"),

    /** Other error. */
    OTHER_ERROR("C2B00016", "Other Error");

    private final String code;
    private final String description;

    ValidationResultCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Returns the Safaricom result code string.
     *
     * @return the result code (e.g. {@code "C2B00012"})
     */
    public String code() {
        return code;
    }

    /**
     * Returns the human-readable description of the result code.
     *
     * @return the result description
     */
    public String description() {
        return description;
    }
}