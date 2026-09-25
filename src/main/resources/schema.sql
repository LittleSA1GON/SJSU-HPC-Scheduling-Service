PRAGMA foreign_keys = ON;

DROP TABLE IF EXISTS appointments;
DROP TABLE IF EXISTS availability_slots;
DROP TABLE IF EXISTS services;
DROP TABLE IF EXISTS providers;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id INTEGER PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    display_name TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('CUSTOMER', 'PROVIDER_ADMIN'))
);

CREATE TABLE providers (
    provider_id INTEGER PRIMARY KEY,
    user_id INTEGER NOT NULL UNIQUE,
    description TEXT,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE services (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0),
    partition_hint TEXT,
    gpu_type TEXT
);

CREATE TABLE availability_slots (
    id INTEGER PRIMARY KEY,
    provider_id INTEGER NOT NULL,
    service_id INTEGER NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    FOREIGN KEY (provider_id) REFERENCES providers(provider_id),
    FOREIGN KEY (service_id) REFERENCES services(id),
    CHECK (end_time > start_time),
    UNIQUE (provider_id, service_id, start_time, end_time)
);

CREATE TABLE appointments (
    id INTEGER PRIMARY KEY,
    customer_id INTEGER NOT NULL,
    slot_id INTEGER NOT NULL,
    service_id INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'BOOKED'
        CHECK (status IN ('BOOKED', 'CANCELLED')),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES users(id),
    FOREIGN KEY (slot_id) REFERENCES availability_slots(id),
    FOREIGN KEY (service_id) REFERENCES services(id),
    CONSTRAINT uq_appointment_slot UNIQUE (slot_id)
);

CREATE INDEX idx_slots_status_start
    ON availability_slots(status, start_time);

CREATE INDEX idx_appointments_customer
    ON appointments(customer_id);
