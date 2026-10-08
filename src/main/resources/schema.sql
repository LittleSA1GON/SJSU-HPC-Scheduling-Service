PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY, username TEXT NOT NULL UNIQUE, email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL, display_name TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('CUSTOMER', 'PROVIDER_ADMIN'))
);
CREATE TABLE IF NOT EXISTS providers (
    provider_id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL UNIQUE REFERENCES users(id), description TEXT
);
CREATE TABLE IF NOT EXISTS services (
    id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE, description TEXT,
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0), partition_hint TEXT, gpu_type TEXT
);
CREATE TABLE IF NOT EXISTS availability_slots (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    provider_id INTEGER NOT NULL REFERENCES providers(provider_id),
    service_id INTEGER NOT NULL REFERENCES services(id),
    start_time TEXT NOT NULL, end_time TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    version INTEGER NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (end_time > start_time), UNIQUE (provider_id, service_id, start_time, end_time)
);
CREATE TABLE IF NOT EXISTS appointments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id INTEGER NOT NULL REFERENCES users(id),
    slot_id INTEGER NOT NULL REFERENCES availability_slots(id),
    service_id INTEGER NOT NULL REFERENCES services(id),
    status TEXT NOT NULL DEFAULT 'BOOKED' CHECK (status IN ('BOOKED', 'CANCELLED', 'COMPLETED')),
    project_name TEXT NOT NULL CHECK (length(project_name) BETWEEN 1 AND 120),
    resource_requirements TEXT NOT NULL DEFAULT '' CHECK (length(resource_requirements) <= 200),
    contact_email TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Cancelled history is retained; exactly one non-cancelled booking per slot.
CREATE UNIQUE INDEX IF NOT EXISTS uq_appointment_slot
    ON appointments(slot_id) WHERE status IN ('BOOKED', 'COMPLETED');
CREATE INDEX IF NOT EXISTS idx_slots_status_start ON availability_slots(status, start_time);
CREATE INDEX IF NOT EXISTS idx_slots_provider_service_start ON availability_slots(provider_id, service_id, start_time);
CREATE INDEX IF NOT EXISTS idx_appointments_customer ON appointments(customer_id);
