/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/16/2026 at 8:24 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.messaging;

import com.mycompany.api.mpesa.service.ResultProcessingService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MpesaResultListener}.
 *
 * <p>Verifies acknowledgement behaviour — ack on success, reject on failure.
 *
 * @author Oualid Gharach
 */
@ExtendWith(MockitoExtension.class)
class MpesaResultListenerTest {

    @Mock
    private ResultProcessingService resultProcessingService;

    @Mock
    private Channel channel;

    @InjectMocks
    private MpesaResultListener mpesaResultListener;

    private ProvisioningResultMessage successResult;
    private ProvisioningResultMessage failureResult;
    private Message amqpMessage;

    @BeforeEach
    void setUp() {
        successResult = new ProvisioningResultMessage(
                UUID.randomUUID(), "NLJ7RT61SV", true, "receipt-123", null);

        failureResult = new ProvisioningResultMessage(
                UUID.randomUUID(), "NLJ7RT61SV", false, null, "Account not found");

        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(42L);
        amqpMessage = new Message(new byte[0], props);
    }

    // =========================================================================
    // Acknowledgement — success
    // =========================================================================

    @Test
    @DisplayName("Acknowledges message after successful result processing")
    void shouldAcknowledgeMessageAfterSuccessfulProcessing() throws Exception {
        mpesaResultListener.onMessage(successResult, amqpMessage, channel);

        verify(channel).basicAck(42L, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("Delegates to ResultProcessingService for success result")
    void shouldDelegateToResultProcessingServiceForSuccessResult() throws Exception {
        mpesaResultListener.onMessage(successResult, amqpMessage, channel);

        verify(resultProcessingService).process(successResult);
    }

    // =========================================================================
    // Rejection — processing failure
    // =========================================================================

    @Test
    @DisplayName("Rejects message without requeue when processing throws")
    void shouldRejectMessageWithoutRequeueWhenProcessingThrows() throws Exception {
        doThrow(new RuntimeException("MongoDB failure"))
                .when(resultProcessingService).process(any());

        mpesaResultListener.onMessage(successResult, amqpMessage, channel);

        verify(channel).basicReject(42L, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    // =========================================================================
    // Failure result — still acknowledged
    // =========================================================================

    @Test
    @DisplayName("Acknowledges message for failure result — business failure not infrastructure failure")
    void shouldAcknowledgeMessageForFailureResult() throws Exception {
        mpesaResultListener.onMessage(failureResult, amqpMessage, channel);

        verify(channel).basicAck(42L, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
    }
}