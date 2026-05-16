/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/16/2026 at 9:49 AM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.messaging;

import com.mycompany.api.mpesa.service.ResultProcessingService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static com.mycompany.api.mpesa.config.AppConfig.MDC_CORRELATION_ID;
import static com.mycompany.api.mpesa.config.AppConfig.MDC_TRANS_ID;

/**
 * RabbitMQ listener that consumes {@link ProvisioningResultMessage} instances
 * from {@code mpesa.results.queue} and delegates state transition to
 * {@link ResultProcessingService}.
 *
 * <p>Manual acknowledgement is used throughout. Messages are acknowledged only
 * after the {@link com.mycompany.api.mpesa.document.MpesaEvent} state has been
 * successfully persisted to MongoDB. If persistence fails, the message is retried
 * up to the configured listener retry limit before being rejected to the DLQ.
 *
 * <p>MDC context is established for the full processing lifecycle, ensuring
 * {@code correlationId} and {@code transId} appear on all log lines emitted
 * during result handling.
 *
 * @author Oualid Gharach
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MpesaResultListener {

    private final ResultProcessingService resultProcessingService;

    /**
     * Consumes a {@link ProvisioningResultMessage} from {@code mpesa.results.queue}.
     *
     * <p>Processing steps:
     * <ol>
     *   <li>Delegates to {@link ResultProcessingService} to transition the
     *       {@link com.mycompany.api.mpesa.document.MpesaEvent} to {@code POSTED}
     *       or {@code SUSPENDED}</li>
     *   <li>Acknowledges the message after successful persistence</li>
     *   <li>On persistence failure, rejects without requeue — message is routed
     *       to {@code results.dlq} for operator review</li>
     * </ol>
     *
     * @param result      the inbound provisioning result
     * @param amqpMessage the raw AMQP message used for manual acknowledgement
     * @param channel     the AMQP channel used for manual acknowledgement
     * @throws IOException if acknowledgement or rejection fails at the AMQP level
     */
    @RabbitListener(queues = "${app.messaging.results-queue}")
    public void onMessage(
            ProvisioningResultMessage result,
            Message amqpMessage,
            Channel channel) throws IOException {

        setupMdc(result);
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();

        log.info("Received provisioning result. paymentReference={} success={}",
                result.paymentReference(), result.success());

        try {
            resultProcessingService.process(result);
            channel.basicAck(deliveryTag, false);
            log.info("Result processed and message acknowledged. paymentReference={} success={}",
                    result.paymentReference(), result.success());

        } catch (Exception e) {
            log.error("Failed to persist result — rejecting to DLQ. paymentReference={} reason={}",
                    result.paymentReference(), e.getMessage());
            channel.basicReject(deliveryTag, false);
        } finally {
            clearMdc();
        }
    }

    // =========================================================================
    // MDC
    // =========================================================================

    /**
     * Populates MDC with tracing context for the duration of message processing.
     *
     * @param result the provisioning result
     */
    private void setupMdc(ProvisioningResultMessage result) {
        MDC.put(MDC_CORRELATION_ID, String.valueOf(result.correlationId()));
        MDC.put(MDC_TRANS_ID, result.paymentReference());
    }

    /**
     * Clears MDC after message processing completes to prevent context leakage
     * across thread pool reuse.
     */
    private void clearMdc() {
        MDC.remove(MDC_CORRELATION_ID);
        MDC.remove(MDC_TRANS_ID);
    }
}