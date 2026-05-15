/*
 * Copyright (c) 2026 Oualid Gharach. All rights reserved.
 *
 * Aiming for production-grade standards through clean code and best practices
 * for educational and informational purposes.
 *
 * Created on: 5/14/2026 at 9:37 PM
 *
 * Feel free to use or contribute. Contact: oualid.gharach@gmail.com
 */
package com.mycompany.api.mpesa.service;

import com.mycompany.api.mpesa.BaseIntegrationTest;
import com.mycompany.api.mpesa.document.MpesaEvent;
import com.mycompany.api.mpesa.document.OutboxEntry;
import com.mycompany.api.mpesa.repository.MpesaEventRepository;
import com.mycompany.api.mpesa.repository.OutboxEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

/**
 * Integration tests for {@link ConfirmationTransactionHelper} transaction semantics.
 *
 * <p>Proves that {@link MpesaEvent} and {@link OutboxEntry} are persisted atomically —
 * if the outbox insert fails, the event insert is rolled back and neither document persists.
 *
 * <p>Uses {@link TransactionTemplate} directly to wrap the spy-based failure injection
 * within a real MongoDB transaction, bypassing the Spring AOP proxy limitation.
 *
 * @author Oualid Gharach
 */
class ConfirmationTransactionHelperIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MongoDatabaseFactory mongoDatabaseFactory;

    @Autowired
    private MpesaEventRepository mpesaEventRepository;

    @Autowired
    private OutboxEntryRepository outboxEntryRepository;

    @BeforeEach
    void setUp() {
        mpesaEventRepository.deleteAll();
        outboxEntryRepository.deleteAll();
    }

    @Test
    @DisplayName("Rolls back MpesaEvent when OutboxEntry insert fails — neither document persists")
    void shouldRollbackEventWhenOutboxInsertFails() {
        MongoTemplate spyTemplate = spy(mongoTemplate);
        ConfirmationTransactionHelper helperWithSpy =
                new ConfirmationTransactionHelper(spyTemplate);

        MpesaEvent event = buildEvent();

        // Force OutboxEntry insert to fail
        doThrow(new RuntimeException("Simulated outbox failure"))
                .when(spyTemplate).insert(any(OutboxEntry.class));

        // Wrap in real MongoDB transaction via TransactionTemplate
        MongoTransactionManager txManager = new MongoTransactionManager(mongoDatabaseFactory);
        TransactionTemplate txTemplate = new TransactionTemplate(txManager);

        assertThatThrownBy(() ->
                txTemplate.execute(status -> {
                    helperWithSpy.persistReceivedWithOutbox(event);
                    return null;
                }))
                .isInstanceOf(RuntimeException.class);

        // Transaction rolled back — neither document should exist
        assertThat(mpesaEventRepository.findAll()).isEmpty();
        assertThat(outboxEntryRepository.findAll()).isEmpty();
    }

    private MpesaEvent buildEvent() {
        MpesaEvent event = new MpesaEvent();
        event.setTransId("NLJ7RT61SV");
        event.setAmount(new BigDecimal("1500.00"));
        event.setBusinessShortCode("600123");
        event.setBillRefNumber("1234567897");
        event.setMsisdn("254712345678");
        return event;
    }
}