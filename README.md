# M-Pesa Gateway

Safaricom Lipa na M-Pesa C2B Paybill integration for the Utility Account Management System.

A dedicated Spring Boot microservice that acts as a protocol translator between Safaricom and the Utility Account (UA) Service. The UA Service remains unchanged and processes M-Pesa payments as if they originated from any other provider.

Full architecture, design decisions, operational procedures, and configuration specifications are documented in `mpesa-architecture-v7.docx`. 


---

## Architecture

```
Safaricom → Nginx → M-Pesa Gateway → RabbitMQ → Provisioning Service → UA Service
                         ↑                                    ↓
                       MongoDB                           RabbitMQ
                     (event store)                    (result message)
                         ↓
                  MpesaResultListener
                         ↓
                       POSTED / SUSPENDED
```

**Validation flow** — synchronous pre-payment check. The Gateway calls the UA Service validation endpoint and returns a Safaricom `ResultCode` response within the 8-second SLA.

**Confirmation flow** — asynchronous post-payment processing. The Gateway persists the event durably before acknowledging Safaricom, then publishes a provisioning request via the Outbox Pattern. The Provisioning Service posts the payment to the UA Service and returns the result. The result listener transitions the event to `POSTED` or `SUSPENDED`.

---

## Technology Stack

| Component | Technology |
|---|---|
| Application | Spring Boot 3.5 / Java 21 |
| Event Store | MongoDB (single-node replica set) |
| Messaging | RabbitMQ |
| Resilience | Resilience4j (circuit breaker, retry) |
| HTTP Client | Apache HttpClient 5 via RestClient |
| Mapping | MapStruct |
| API Docs | SpringDoc OpenAPI |

---

## Package Structure

```
com.mycompany.api.mpesa
├── client/          # UaValidationClient — outbound UA Service HTTP calls
├── config/          # AppConfig, MessagingConfig, RestClientConfig, properties records
├── controller/      # ValidationController, ConfirmationController
├── document/        # MpesaEvent, OutboxEntry (MongoDB documents)
├── dto/             # CallbackRequest, ValidationResponse
├── enums/           # MpesaEventState, OutboxStatus, ValidationResultCode
├── filter/          # CallbackTokenFilter, CorrelationIdFilter
├── mapper/          # MpesaEventMapper (MapStruct)
├── messaging/       # OutboxProcessor, MpesaResultListener, message records
├── repository/      # MpesaEventRepository, OutboxEntryRepository
├── service/         # ConfirmationService, ValidationService, ResultProcessingService
├── util/            # BillRefNormaliser
└── validation/      # ValidBillRef, ValidShortCode constraints
```

---

## Event Lifecycle

```
Safaricom callback received
    → MpesaEvent: RECEIVED + OutboxEntry: PENDING (atomic)
    → OutboxProcessor publishes PaymentProvisioningMessage
    → OutboxEntry: SENT
    → Provisioning Service posts to UA Service
    → ProvisioningResultMessage published to mpesa.results.queue
    → MpesaResultListener consumes result
    → MpesaEvent: POSTED (success) or SUSPENDED (failure)
```

**State invariants:**
- `POSTED` — has `billingReceipt` and `postedAt`. Immutable terminal state.
- `SUSPENDED` — has `failureReason`. Recoverable by ops via outbox replay.
- `RECEIVED` — has neither. Awaiting provisioning.

---

## Running Locally

**Prerequisites:** Docker (MongoDB, RabbitMQ), UA Service on port 8080.

```bash
mvn clean package
java -jar target/mpesa-gateway-1.0.0-SNAPSHOT.jar
```

The `dev` profile loads automatically. Swagger UI:

```
http://localhost:8081/swagger-ui.html
```

Health check:

```
http://localhost:8081/actuator/health
```

**Dev profile requires:**
- `mongodb-dev` container running (replica set `rs0`)
- `rabbitmq-dev` container running (vhost `/payments`)
- UA Service running on `localhost:8080`

---

## Testing

```bash
mvn clean package
```

106 tests across unit, integration, and end-to-end pipeline scenarios.

| Category | Tests |
|---|---|
| Unit | 67 |
| Integration (MongoDB via Testcontainers) | 19 |
| End-to-end pipeline | 3 |
| WebMvcTest (controller) | 10 |
| WireMock (HTTP client) | 7 |
| **Total** | **106** |

---

## Tracing

Every log line includes `correlationId` and `transId` via MDC. The correlation ID originates at the Nginx edge and propagates through all services via `X-Correlation-ID` header and AMQP `correlation-id` property.

To trace a transaction end-to-end, search logs by `transId` (Safaricom transaction reference), then use `correlationId` to follow the full lifecycle across all services.

---

*Part of the Utility Account Management System. See also: `utility-account`, `provisioning-service`.*

*Author: Oualid Gharach · [oualid.gharach@gmail.com](mailto:oualid.gharach@gmail.com)*
