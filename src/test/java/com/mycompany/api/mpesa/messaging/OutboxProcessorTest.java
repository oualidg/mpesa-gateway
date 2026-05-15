/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/15/2026 at 9:02 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.messaging;

import com.mongodb.client.result.UpdateResult;
import com.mycompany.api.mpesa.config.MessagingConfig.MessagingProperties;
import com.mycompany.api.mpesa.config.OutboxProperties;
import com.mycompany.api.mpesa.document.MpesaEvent;
import com.mycompany.api.mpesa.document.OutboxEntry;
import com.mycompany.api.mpesa.util.BillRefNormaliser.ReferenceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OutboxProcessor}.
 *
 * <p>Verifies message building and RabbitMQ publish delegation.
 * MongoDB interaction is tested via integration tests.
 *
 * @author Oualid Gharach
 */

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxProcessorTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private OutboxProperties outboxProperties;

    @Mock
    private MessagingProperties messagingProperties;

    @InjectMocks
    private OutboxProcessor outboxProcessor;

    private static final UUID CORRELATION_ID = UUID.randomUUID();
    private static final String EXCHANGE = "payments.exchange";
    private static final String ROUTING_KEY = "provisioning.request";
    private static final String REPLY_TO = "mpesa.result";

    @BeforeEach
    void setUp() {
        when(messagingProperties.exchange()).thenReturn(EXCHANGE);
        when(messagingProperties.provisioningRoutingKey()).thenReturn(ROUTING_KEY);
        when(messagingProperties.mpesaResultRoutingKey()).thenReturn(REPLY_TO);
        when(outboxProperties.batchSize()).thenReturn(100);
        when(outboxProperties.leaseTimeout()).thenReturn(Duration.ofSeconds(30));
        when(outboxProperties.pollInterval()).thenReturn(Duration.ofSeconds(5));
        when(mongoTemplate.updateMulti(any(), any(), eq(OutboxEntry.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));
    }

    // =========================================================================
    // Message Building — Account
    // =========================================================================

    @Test
    @DisplayName("Builds provisioning message with accountNumber for ACCOUNT reference type")
    void shouldBuildMessageWithAccountNumberForAccountReferenceType() {
        MpesaEvent event = buildEvent("1234567897", ReferenceType.ACCOUNT);

        when(mongoTemplate.findById(any(), eq(MpesaEvent.class))).thenReturn(event);
        when(mongoTemplate.findAndModify(any(), any(),
                any(org.springframework.data.mongodb.core.FindAndModifyOptions.class),
                eq(OutboxEntry.class)))
                .thenReturn(OutboxEntry.forEvent(new org.bson.types.ObjectId()))
                .thenReturn(null);

        outboxProcessor.process();

        verify(rabbitTemplate).convertAndSend(
                eq(EXCHANGE),
                eq(ROUTING_KEY),
                argThat((Object msg) -> {
                    PaymentProvisioningMessage m = (PaymentProvisioningMessage) msg;
                    return m.accountNumber().equals("1234567897")
                            && m.customerNumber() == null
                            && m.paymentReference().equals("NLJ7RT61SV")
                            && m.correlationId().equals(CORRELATION_ID);
                }));
    }

    // =========================================================================
    // Message Building — Customer
    // =========================================================================

    @Test
    @DisplayName("Builds provisioning message with customerNumber for CUSTOMER reference type")
    void shouldBuildMessageWithCustomerNumberForCustomerReferenceType() {
        MpesaEvent event = buildEvent("12345674", ReferenceType.CUSTOMER);

        when(mongoTemplate.findById(any(), eq(MpesaEvent.class))).thenReturn(event);
        when(mongoTemplate.findAndModify(any(), any(),
                any(org.springframework.data.mongodb.core.FindAndModifyOptions.class),
                eq(OutboxEntry.class)))
                .thenReturn(OutboxEntry.forEvent(new org.bson.types.ObjectId()))
                .thenReturn(null);

        outboxProcessor.process();

        verify(rabbitTemplate).convertAndSend(
                eq(EXCHANGE),
                eq(ROUTING_KEY),
                argThat((Object msg) -> {
                    PaymentProvisioningMessage m = (PaymentProvisioningMessage) msg;
                    return m.customerNumber().equals("12345674")
                            && m.accountNumber() == null;
                }));
    }

    // =========================================================================
    // Missing Event
    // =========================================================================

    @Test
    @DisplayName("Does not publish when referenced MpesaEvent is missing")
    void shouldNotPublishWhenReferencedEventIsMissing() {
        when(mongoTemplate.findById(any(), eq(MpesaEvent.class))).thenReturn(null);
        when(mongoTemplate.findAndModify(any(), any(),
                any(org.springframework.data.mongodb.core.FindAndModifyOptions.class),
                eq(OutboxEntry.class)))
                .thenReturn(OutboxEntry.forEvent(new org.bson.types.ObjectId()))
                .thenReturn(null);

        outboxProcessor.process();

        verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(Object.class));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private MpesaEvent buildEvent(String billRef, ReferenceType type) {
        MpesaEvent event = new MpesaEvent();
        event.setTransId("NLJ7RT61SV");
        event.setAmount(new BigDecimal("1500.00"));
        event.setBillRefNumber(billRef);
        event.setResolvedReferenceType(type);
        event.setCorrelationId(CORRELATION_ID);
        return event;
    }
}