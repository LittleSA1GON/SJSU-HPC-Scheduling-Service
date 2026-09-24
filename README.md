# CMPE 172 Milestone 1 - HPC Resource Reservation

This repository contains the Milestone 1 Spring Boot skeleton for an **HPC
Resource Reservation and Job Scheduling** scenario. It models the five required
course entities (`users`, `providers`, `services`, `availability_slots`, and
`appointments`) and exposes two database-backed read endpoints.

Milestone 1 uses simulated HPC availability. It does not require access to the
real SJSU HPC cluster or Slurm.

## Implemented

- Java 21 and Spring Boot 3.3
- Spring Web and Spring JDBC (`JdbcTemplate`)
- SQLite with hand-written SQL; no JPA, Hibernate, or other ORM
- Controller -> Service -> Repository -> Database layering
- `GET /` home summary DTO populated from database counts
- `GET /slots` available-slot DTOs populated by a SQL join
- Five-entity schema with primary keys, foreign keys, checks, and indexes
- Database-level double-booking protection with `UNIQUE (slot_id)`
- Per-connection SQLite foreign-key enforcement
- Seeded providers, services, and simulated compute slots
- Mock notification boundary and optional mock HPC boundary
- Integration tests for endpoints, seed data, and database constraints

## Request flow

```text
HTTP request
   -> Spring DispatcherServlet (Front Controller)
   -> Controller
   -> Service
   -> Repository
   -> JdbcTemplate
   -> SQLite
   -> DTO response
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for the design details.

## Requirements

- JDK 21 or newer
- Maven 3.9+

## Build and test

```bash
mvn clean test
```

The tests use a database under `target/`, rebuild the schema, and load the
sample data automatically.

## Run

```bash
mvn spring-boot:run
```

The application listens on port 8080 and rebuilds the Milestone 1 demo database
on each startup.

```bash
curl http://localhost:8080/
curl http://localhost:8080/slots
```

Expected seed-data summary:

- 1 provider
- 3 services
- 3 available slots

## API

### `GET /`

Returns application metadata and database-derived provider, service, and
available-slot counts.

### `GET /slots`

Returns available reservation windows joined with their provider and compute
service details. A slot with a `BOOKED` appointment is omitted.

## Database design

The five required entities are:

- `users`: customer and provider/admin accounts
- `providers`: provider profile linked one-to-one to a provider/admin user
- `services`: app-level HPC compute services
- `availability_slots`: simulated compute windows owned by a provider and tied
  to a service
- `appointments`: customer reservations linking a user, slot, and service

Important integrity rules include:

- `UNIQUE (slot_id)` to prevent double-booking
- a composite `(slot_id, service_id)` foreign key to prevent a reservation from
  naming a service different from the selected slot
- checks for roles, statuses, nonnegative prices, positive durations, and slot
  time order
- Hikari connection initialization that enables SQLite foreign keys on every
  pooled connection

## Milestone scope

Authentication, booking/cancellation endpoints, the concurrent booking
transaction, notifications, observability, deployment, and AI features belong
to later milestones and are intentionally not implemented here.

Seeded password hashes are placeholders because login is outside Milestone 1.
Replace them with BCrypt hashes before implementing authentication.

## Walkthrough video

Add the unlisted walkthrough video link here before submitting Milestone 1.
