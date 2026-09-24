# CMPE 172 Milestone 1 - HPC Resource Reservation

Running Spring Boot skeleton for a simulated HPC resource reservation system.

## Milestone 1 deliverable

- Java 21 and Spring Boot 3.3
- Spring Web and Spring JDBC with hand-written SQL
- SQLite database; no ORM
- Controller -> Service -> Repository -> Database layering
- DTO responses from `GET /` and `GET /slots`
- Required `users`, `providers`, `services`, `availability_slots`, and
  `appointments` tables
- Seed data and a database-level `UNIQUE (slot_id)` double-booking guard

Only the Milestone 1 skeleton is implemented. Authentication, booking and
cancellation workflows, notifications, deployment, and AI features belong to
later milestones.

## Build and run

Requirements: JDK 21+ and Maven 3.9+.

```bash
mvn clean test
mvn spring-boot:run
```

The demo database is rebuilt and seeded at startup. No environment variables
are required.

```text
GET http://localhost:8080/
GET http://localhost:8080/slots
```

Expected seed data: 1 provider, 3 services, and 3 available slots.

## Walkthrough video

Add the unlisted video URL before submission.
