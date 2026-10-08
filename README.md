# SJSU HPC Scheduling Service — Milestone 2

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

12 service unit tests and 13 integration tests cover validation, ownership, RBAC/CSRF, SQL pagination, rollback, rebooking, provider management, completion, and startup persistence. The two-thread HTTP booking test asserts exactly one 201, one 409, one stored appointment, and one version increment. Tests use separate file-backed databases under `target/`.
