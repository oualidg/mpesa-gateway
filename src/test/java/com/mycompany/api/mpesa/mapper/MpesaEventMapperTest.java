/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/14/2026 at 9:01 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.mapper;

import com.mycompany.api.mpesa.document.MpesaEvent;
import com.mycompany.api.mpesa.dto.CallbackRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MpesaEventMapper}.
 *
 * <p>Verifies field mapping, string trimming, transId null normalisation,
 * and lifecycle field exclusion. No Spring context required — MapStruct
 * generates the implementation at compile time.
 *
 * @author Oualid Gharach
 */
class MpesaEventMapperTest {

    private MpesaEventMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(MpesaEventMapper.class);
    }

    // =========================================================================
    // Field Mapping
    // =========================================================================

    @Test
    @DisplayName("Maps all Safaricom callback fields to MpesaEvent correctly")
    void shouldMapAllFieldsCorrectly() {
        CallbackRequest request = new CallbackRequest(
                "Pay Bill",
                "NLJ7RT61SV",
                "20240315123045",
                new BigDecimal("1500.00"),
                "600123",
                "1234567897",
                "INV-001",
                new BigDecimal("50000.00"),
                "EXT-001",
                "254712345678",
                "John",
                "K",
                "Doe"
        );

        MpesaEvent event = mapper.toMpesaEvent(request);

        assertThat(event.getTransId()).isEqualTo("NLJ7RT61SV");
        assertThat(event.getTransactionType()).isEqualTo("Pay Bill");
        assertThat(event.getTransTime()).isEqualTo("20240315123045");
        assertThat(event.getAmount()).isEqualByComparingTo("1500.00");
        assertThat(event.getBusinessShortCode()).isEqualTo("600123");
        assertThat(event.getBillRefNumber()).isEqualTo("1234567897");
        assertThat(event.getInvoiceNumber()).isEqualTo("INV-001");
        assertThat(event.getOrgAccountBalance()).isEqualByComparingTo("50000.00");
        assertThat(event.getThirdPartyTransId()).isEqualTo("EXT-001");
        assertThat(event.getMsisdn()).isEqualTo("254712345678");
        assertThat(event.getFirstName()).isEqualTo("John");
        assertThat(event.getMiddleName()).isEqualTo("K");
        assertThat(event.getLastName()).isEqualTo("Doe");
    }

    // =========================================================================
    // String Trimming
    // =========================================================================

    @Test
    @DisplayName("Trims whitespace from all string fields")
    void shouldTrimWhitespaceFromAllStringFields() {
        CallbackRequest request = new CallbackRequest(
                "  Pay Bill  ",
                "  NLJ7RT61SV  ",
                "  20240315123045  ",
                new BigDecimal("1500.00"),
                "  600123  ",
                "  1234567897  ",
                null, null, null,
                "  254712345678  ",
                "  John  ",
                null,
                "  Doe  "
        );

        MpesaEvent event = mapper.toMpesaEvent(request);

        assertThat(event.getTransId()).isEqualTo("NLJ7RT61SV");
        assertThat(event.getTransactionType()).isEqualTo("Pay Bill");
        assertThat(event.getTransTime()).isEqualTo("20240315123045");
        assertThat(event.getBusinessShortCode()).isEqualTo("600123");
        assertThat(event.getBillRefNumber()).isEqualTo("1234567897");
        assertThat(event.getMsisdn()).isEqualTo("254712345678");
        assertThat(event.getFirstName()).isEqualTo("John");
        assertThat(event.getLastName()).isEqualTo("Doe");
    }

    // =========================================================================
    // TransId Null Normalisation
    // =========================================================================

    @Test
    @DisplayName("Normalises blank transId to null for sparse index consistency")
    void shouldNormaliseBlankTransIdToNull() {
        CallbackRequest request = new CallbackRequest(
                "Pay Bill", "   ", "20240315123045",
                new BigDecimal("1500.00"), "600123", "1234567897",
                null, null, null, "254712345678", null, null, null
        );

        MpesaEvent event = mapper.toMpesaEvent(request);

        assertThat(event.getTransId()).isNull();
    }

    @Test
    @DisplayName("Normalises empty transId to null for sparse index consistency")
    void shouldNormaliseEmptyTransIdToNull() {
        CallbackRequest request = new CallbackRequest(
                "Pay Bill", "", "20240315123045",
                new BigDecimal("1500.00"), "600123", "1234567897",
                null, null, null, "254712345678", null, null, null
        );

        MpesaEvent event = mapper.toMpesaEvent(request);

        assertThat(event.getTransId()).isNull();
    }

    // =========================================================================
    // Lifecycle Fields Excluded
    // =========================================================================

    @Test
    @DisplayName("Does not set lifecycle fields — left for service layer")
    void shouldNotSetLifecycleFields() {
        CallbackRequest request = new CallbackRequest(
                "Pay Bill", "NLJ7RT61SV", "20240315123045",
                new BigDecimal("1500.00"), "600123", "1234567897",
                null, null, null, "254712345678", null, null, null
        );

        MpesaEvent event = mapper.toMpesaEvent(request);

        assertThat(event.getState()).isNull();
        assertThat(event.getBillingReceipt()).isNull();
        assertThat(event.getFailureReason()).isNull();
        assertThat(event.getCreatedAt()).isNull();
        assertThat(event.getPostedAt()).isNull();
        assertThat(event.getUpdatedAt()).isNull();
        assertThat(event.getCorrelationId()).isNull();
        assertThat(event.getResolvedReferenceType()).isNull();
    }

    // =========================================================================
    // Null Fields
    // =========================================================================

    @Test
    @DisplayName("Preserves null optional fields without converting to empty string")
    void shouldPreserveNullOptionalFields() {
        CallbackRequest request = new CallbackRequest(
                "Pay Bill", "NLJ7RT61SV", "20240315123045",
                new BigDecimal("1500.00"), "600123", "1234567897",
                null, null, null, "254712345678", null, null, null
        );

        MpesaEvent event = mapper.toMpesaEvent(request);

        assertThat(event.getInvoiceNumber()).isNull();
        assertThat(event.getOrgAccountBalance()).isNull();
        assertThat(event.getThirdPartyTransId()).isNull();
        assertThat(event.getFirstName()).isNull();
        assertThat(event.getMiddleName()).isNull();
        assertThat(event.getLastName()).isNull();
    }
}