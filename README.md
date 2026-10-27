# SJSU HPC Scheduling Service — Milestone 3

![CI](https://github.com/LittleSA1GON/SJSU-HPC-Scheduling-Service/actions/workflows/ci.yml/badge.svg)

A booking system for simulated HPC resources with customer reservations and provider availability management.

## Stack

Java 21, Maven 3.9+, Spring Boot 3.3.5, Thymeleaf, Spring Security, handwritten JDBC SQL, and SQLite.

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

35 tests cover the M2 booking/security/concurrency behavior plus notification success, retries, timeouts, 4xx, deduplication, validation, health, metrics, and booking during an outage. Tests use separate file-backed databases under `target/`. Run `mvn -B verify`; CI runs this suite on every push and pull request, then builds and smoke-tests compose.

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
