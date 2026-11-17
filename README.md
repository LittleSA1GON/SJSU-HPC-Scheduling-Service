# SJSU HPC Scheduling Service — Milestone 4

![CI](https://github.com/LittleSA1GON/SJSU-HPC-Scheduling-Service/actions/workflows/ci.yml/badge.svg)

A booking system for simulated HPC resources with customer reservations and provider availability management.

## Stack

Java 21, Maven 3.9+, Spring Boot 3.5.7, Spring AI 1.1.8, Thymeleaf, Spring Security, handwritten JDBC SQL, and SQLite.

## Run

```bash
mvn test
mvn spring-boot:run
```

Open http://127.0.0.1:8080/. Times use `America/Los_Angeles`; startup preserves data and adds missing future demo availability.

## Demo accounts

| Account | Role | Password |
| --- | --- | --- |
| `student-researcher`, `second-researcher` | CUSTOMER | `HpcDemo172!` |
| `hpc-admin`, `lab-admin` | PROVIDER_ADMIN | `HpcDemo172!` |

These public demo credentials are stored as BCrypt hashes. Login uses server-side sessions and JDBC roles; modifying requests require CSRF tokens.

## API

| Method / endpoint | Access | Purpose |
| --- | --- | --- |
| `GET /api/auth/csrf` | Public | CSRF token and header name |
| `POST /api/auth/login` | Public, CSRF | URL-encoded username/password; creates session |
| `GET /api/auth/me` | Public | Signed-in identity/role; 204 when anonymous |
| `POST /api/auth/logout` | Session, CSRF | Invalidate session |
| `GET /api/catalog` | Public | Services, providers, date, time zone |
| `GET /api/slots` | Public | Filter by provider/service/date/duration; SQL pagination |
| `POST /api/appointments` | Customer, CSRF | Book with slot/service IDs, version, project, requirements, email |
| `GET /api/appointments` | Customer | Own upcoming/history/all reservations |
| `GET /api/appointments/{id}` | Customer owner | Reservation details |
| `POST /api/appointments/{id}/cancel` | Customer owner, CSRF | Cancel before start; retain history |
| `GET /api/provider/slots` | Provider | Own availability, including CLOSED slots |
| `POST /api/provider/slots` | Provider, CSRF | Create service availability with future start/end times |
| `DELETE /api/provider/slots/{id}?version=...` | Owning provider, CSRF | Close an unbooked slot |
| `GET /api/provider/appointments` | Provider | Reservations booked with this provider |

Pagination uses zero-based `page` and `size` 1–100. Slot filters: `providerId`, `serviceId`, `date`, `durationMinutes`, `availableOnly`. Reservation views: `upcoming`, `history`, `all`. Statuses: BOOKED, CANCELLED, COMPLETED. Input errors return standard JSON without stack traces; wrong-role requests return 403.

## Concurrency design

Booking uses an optimistic slot `version` claim inside a `SERIALIZABLE` transaction.
The conditional update and appointment insert commit together; insert failure rolls back the claim.
The partial unique `uq_appointment_slot` index permits cancelled history and prevents duplicate BOOKED/COMPLETED appointments.
Owner-only cancellation releases availability and increments the slot version; removal preserves CLOSED slots and history.
SQLite WAL serializes writers; BUSY/LOCKED errors retry the entire transaction at most three times through separate transaction beans.
Logical conflicts return 409; exhausted database contention returns 503.

## Tests

44 tests cover booking/security/concurrency, notifications, AI confirmation and ownership, double-booking through the agent's real transactional path, vector retrieval, conversation context, and generated reminder delivery/retry/deduplication. AI tests use deterministic model doubles and require no API key. Tests use file-backed databases under `target/`. Run `mvn -B verify`; CI runs this suite on every push and pull request, then builds and smoke-tests compose.

## Notification boundary

`POST /notification-service/v1/notifications` is a public, CSRF-exempt mock external service. One coarse-grained JSON DTO carries `recipient`, `type`, `message`, `appointmentId`, and `idempotencyKey`; one call carries the complete notification without remote lookups. Types are `BOOKING_CONFIRMED`, `BOOKING_CANCELLED`, and reserved `REMINDER`.

Booking and cancellation commit first, then call the gateway outside the database transaction. `appointment-{id}-{TYPE}` goes in the body and `Idempotency-Key` header. The mock stores accepted keys in memory: first acceptance returns 201/ACCEPTED; duplicates return 200/DUPLICATE. Deduplication resets on restart; there is no recipient-reading endpoint.

The client connects within 500ms, reads within 1s, and tries at most three times with 200ms/400ms backoff on connection, timeout, or server failure. A 4xx stops retries. Fallback logs WARN, increments the failure counter, and returns false while the committed booking stands. Restore the notification service to recover future deliveries; failed notifications have no durable replay queue. Override `HPC_NOTIFICATION_BASE_URL` to point at another service (default: this app).

## Observability

Public `GET /health` returns aggregate status and components, including `components.db.status` from a database-connectivity check. `GET /metrics/hpc.bookings` exposes created/rejected counts; `/metrics/hpc.booking.latency` records successful booking duration before notification; `/metrics/hpc.notifications` counts sent/failed deliveries by type. Use `?tag=outcome:created` to filter a booking metric.

Logs identify appointments, slots, and customers without logging email addresses or message bodies.

| Level | Where | Message |
| --- | --- | --- |
| INFO | BookingService | `Booking created appointmentId=… slotId=… customerId=…` / `Booking cancelled appointmentId=…` |
| INFO | NotificationClient | `Notification sent type=… appointmentId=… attempt=…` |
| INFO | MockNotificationController | `Notification accepted id=… type=… appointmentId=…` / duplicate ignored |
| WARN | BookingService | `Booking rejected slotId=… status=409: …` |
| WARN | NotificationClient | per-attempt failure, and the final fallback "booking unaffected" |
| WARN | SqliteRetry | `SQLite busy (attempt n/3); retrying transaction` |
| ERROR | GlobalExceptionHandler | `Request failed: {uri}` with the stack trace (server log only, never returned to the client) |

## Docker

Run `docker compose up --build`, then open http://localhost:8080 and check http://localhost:8080/health. The multi-stage Java 21 image runs as uid 10001. SQLite is embedded, with `HPC_DB_PATH=/data/hpc.db` on the persistent `hpc-data` named volume; compose needs only the app. A curl healthcheck checks database connectivity. `docker compose down` stops the app and preserves reservations.

## Milestone 4: enterprise AI

The scheduler runs normally without a Gemini key. AI endpoints return 503 when the `ai` profile is disabled. To enable in PowerShell, set environment variables for the current shell, then start the app:

```powershell
$env:GEMINI_API_KEY = '<your Google AI Studio API key>'
$env:SPRING_PROFILES_ACTIVE = 'ai'
mvn spring-boot:run
# Or: docker compose up --build
```

Only API-key authentication is configured: do not add `project-id` or `location`, which select Vertex AI. The model defaults to `gemini-4-hydrogen`, from Google's current model catalog, and can be overridden with `GEMINI_MODEL` for your account's available free-tier model. Embeddings default to the supported text model `gemini-embedding-001`, overridden with `GEMINI_EMBEDDING_MODEL`. Model availability and quotas depend on the key. Keep keys out of source control; `.env` files are ignored. Compose reads `.env`; a local Maven run uses environment variables.

Sign in as `student-researcher` and open **AI Assistant**. Try: "Find the earliest GPU slot this week for my model training project." The model can call `catalog`, `checkAvailability`, `listMyAppointments`, `book`, and `cancel`. It can use multiple tools per turn and retains the latest 20 user/assistant messages in the HTTP session. Ask follow-up questions or request a reply in Vietnamese or Spanish (the multilingual bonus). A live smoke test verified Spanish policy output, availability/book/cancel tool use, confirmation, follow-up listing, and reminder generation. Other model responses still require review.

`book` and `cancel` only prepare proposals. The server supplies a separate exact summary and a random confirmation token valid for five minutes. Only the user pressing **Confirm booking/cancellation** calls `/api/ai/confirm`; the model never receives that token or a confirmation tool. A message saying "yes" does not commit. New messages replace old proposals, tokens belong to one customer/session and are single-use, and reset/logout clears the conversation. Availability is rechecked by the existing transaction on confirmation; a proposal does not hold a slot. Conflicts return 409; search again for alternatives. All customer AI routes require CUSTOMER role and CSRF on mutations. Raw user IDs cannot be supplied to the model tools.

`knowledge/hpc-policy.txt` contains six explicitly synthetic policy/FAQ sections. On first chat, Gemini embeds the sections into an in-memory `SimpleVectorStore`; similarity search retrieves up to three excerpts with cosine similarity >= 0.3. The assistant receives those excerpts and source IDs, is instructed to cite them, and must admit missing evidence. The API also returns retrieved source IDs independently of generated text; these identify retrieved evidence, not proof of every claim. The store reingests after restart; first-turn latency includes ingestion. Model/embedding failures produce a safe 503 without committing a booking.

`ReminderAgent` runs with Spring `@Scheduled` every five minutes, starting after one minute, while the AI profile is enabled. It scans at most 100 BOOKED reservations starting in the next 24 hours, generates a short project-specific preparation sentence with the LLM, adds the authoritative time/ID, rechecks status, and sends a REMINDER through the existing notification gateway. Accepted reminders get a durable SQLite receipt; failed generation/delivery remains eligible for later sweeps. A stable idempotency key is used on retries. The manual endpoint requires PROVIDER_ADMIN and CSRF and scans only the signed-in provider's reservations. The internal scheduler scans all providers. The existing gateway is a mock boundary: no real email or SMS is sent.

| Endpoint | Purpose |
| --- | --- |
| `POST /api/ai/chat` | Customer; JSON `{"message":"Can I cancel a booking?"}`; returns answer, retrieved sources, optional proposal |
| `POST /api/ai/confirm` | Customer; JSON `{"token":"<proposal token>"}`; commits the exact staged action |
| `DELETE /api/ai/chat` | Customer; clears history and pending action |
| `POST /api/ai/reminders/run` | Provider; manual reminder sweep, returns scanned/sent/failed counts |

For the reminder demo, publish a slot starting in the next 24 hours, book it as a customer, then sign in as its provider and POST the manual trigger with the session's CSRF header (get token/header from `/api/auth/csrf`). Trigger again to demonstrate the durable receipt prevents another send. Configurable scheduling properties: `hpc.ai.reminder-delay-ms`, `hpc.ai.reminder-initial-delay-ms`.

Limitations: session memory is not persistent; RAG and multilingual output remain probabilistic; chat and relevant tool data are sent to Gemini. The reminder model sees service/project data but no recipient email. Use non-sensitive demo data. Reminder deduplication assumes one app instance. A crash after notification acceptance but before receipt insertion can retry delivery; the mock's in-memory idempotency map resets on restart. A cancellation can still race the final send. Live Gemini testing observed a free-tier limit of five generate-content requests per minute, including internal tool round trips; pace the demo and retry after the quota window on 503. Docker must still be verified in the target environment.

References: [Spring AI Google GenAI chat](https://docs.spring.io/spring-ai/reference/1.1/api/chat/google-genai-chat.html), [embeddings](https://docs.spring.io/spring-ai/reference/1.1/api/embeddings/google-genai-embeddings-text.html), [Gemini models](https://ai.google.dev/gemini-api/docs/models), [Gemini embeddings](https://ai.google.dev/gemini-api/docs/embeddings). Report text and code walkthrough guide: `docs/milestone4/`.
