/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/13/2026 at 2:17 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Outbound response DTO for Safaricom C2B validation callbacks.
 *
 * <p>Safaricom expects a JSON response with {@code ResultCode} and {@code ResultDesc}
 * fields. A {@code ResultCode} of {@code "0"} indicates acceptance; any non-zero
 * code indicates rejection.
 *
 * <p>Static factory methods are provided for the common acceptance and rejection
 * cases to avoid constructing responses inline in the controller or service.
 *
 * @param resultCode the Safaricom result code — {@code "0"} for acceptance,
 *                   {@code "C2B000xx"} for rejection
 * @param resultDesc human-readable description of the validation outcome
 *
 * @author Oualid Gharach
 */
public record ValidationResponse(

        @JsonProperty("ResultCode")
        String resultCode,

        @JsonProperty("ResultDesc")
        String resultDesc

) {
    private static final String ACCEPTED_CODE = "0";
    private static final String ACCEPTED_DESC = "Accepted";

    /**
     * Returns an acceptance response — {@code ResultCode "0"}.
     *
     * @return accepted {@link ValidationResponse}
     */
    public static ValidationResponse accepted() {
        return new ValidationResponse(ACCEPTED_CODE, ACCEPTED_DESC);
    }

    /**
     * Returns a rejection response with the given Safaricom error code and description.
     *
     * @param resultCode the Safaricom rejection code (e.g. {@code "C2B00012"})
     * @param resultDesc human-readable rejection reason
     * @return rejected {@link ValidationResponse}
     */
    public static ValidationResponse rejected(String resultCode, String resultDesc) {
        return new ValidationResponse(resultCode, resultDesc);
    }
}