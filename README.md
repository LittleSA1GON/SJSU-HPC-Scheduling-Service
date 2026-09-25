# SJSU HPC Scheduling Service - CMPE 172 Milestone 1

Spring Boot and SQLite skeleton for a simulated HPC reservation service.

## Included

- Java 21, Spring Boot 3.3, Spring Web, and Spring JDBC
- Hand-written SQL through `JdbcTemplate`; no ORM
- Controller -> Service -> Repository -> Database layering
- `GET /` and `GET /slots` DTO endpoints
- `schema.sql` matching the submitted five-entity ERD
- `seed.sql` with sample users, provider, services, and slots
- Database-level `UNIQUE (slot_id)` double-booking protection
- Submitted ERD, HCI wireframe, report text, and video walkthrough text under `docs/`

The wireframe calls a booking a reservation. The submitted ERD and database use the entity name `appointments`; both terms refer to the same booking record.

## Build and run

Requirements: JDK 21+ and Maven 3.9+.

```bash
mvn clean test
mvn spring-boot:run
```

The demo database is recreated and seeded at startup.

```text
GET http://localhost:8080/
GET http://localhost:8080/slots
```

Expected seed data: 2 users, 1 provider, 3 services, and 3 available slots.

## Milestone 1 scope

This repository implements only the required database-reading skeleton. Authentication, booking commands, cancellation, the interactive wireframe screens, notifications, observability, and AI features belong to later milestones.
