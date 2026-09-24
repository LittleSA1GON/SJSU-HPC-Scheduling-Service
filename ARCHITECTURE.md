# Milestone 1 Architecture

## Request flow

```text
Client / Browser
      |
      | HTTP GET / or GET /slots
      v
Spring DispatcherServlet
(Front Controller)
      |
      v
@RestController
      |
      v
@Service
      |
      v
@Repository
      |
      v
JdbcTemplate
      |
      v
SQLite
```

Spring's `DispatcherServlet` is the Front Controller. Controllers translate
HTTP requests into application calls, services provide the use-case boundary,
repositories contain hand-written SQL, and controllers return DTOs rather than
database rows.

## Required external boundary

```text
Spring Boot Service Layer
          |
          v
NotificationGateway
          |
          v
MockNotificationGateway
          |
          v
Mock external notification service
```

Milestone 1 defines this boundary only. Booking/cancellation and
commit-then-notify behavior are implemented in later milestones.

## Optional HPC boundary

```text
Spring Boot Service Layer
          |
          v
HpcGateway
          |
          v
MockHpcGateway      (Milestone 1)
SlurmHpcGateway     (possible later extension)
          |
          v
SJSU HPC / Slurm
```

The optional abstraction lets Milestone 1 run without cluster credentials.

## Data model

```text
users 1 ---------------- 0..1 providers
users 1 ---------------- M appointments
providers 1 ------------ M availability_slots
services 1 ------------- M availability_slots
services 1 ------------- M appointments
availability_slots 1 --- 0..1 appointments
```

The database contains exactly the five required course entities.
`appointments.slot_id` is unique so two appointments cannot claim the same
slot. A composite foreign key also guarantees that an appointment's service
matches the service assigned to its slot.
