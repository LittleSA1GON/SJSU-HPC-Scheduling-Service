-- Illustrative Milestone 1 data only. These are not live SJSU HPC reservations.
INSERT INTO users (id, username, email, password_hash, display_name, role) VALUES
(1, 'hpc-admin', 'hpc-admin@example.edu', '$2a$10$MILESTONE1PLACEHOLDERHASH000000000000000000000000000', 'HPC Resource Administrator', 'PROVIDER_ADMIN'),
(2, 'student-researcher', 'student@example.edu', '$2a$10$MILESTONE1PLACEHOLDERHASH000000000000000000000000000', 'Sample Student Researcher', 'CUSTOMER');

INSERT INTO providers (id, user_id, display_name, description) VALUES
(1, 1, 'SJSU CoE HPC Resource Manager', 'Provider profile for managing simulated app-level HPC resource availability.');

INSERT INTO services (id, name, description, duration_minutes, price_cents, partition_hint, gpu_type) VALUES
(1, 'CPU Batch Compute', 'App-level reservation for a CPU batch workload.', 120, 0, 'compute', NULL),
(2, 'A100 GPU Compute', 'App-level reservation for an accelerated GPU workload.', 120, 0, 'gpu', 'A100'),
(3, 'H100 GPU Compute', 'App-level reservation for an accelerated GPU workload.', 120, 0, 'gpu', 'H100');

INSERT INTO availability_slots (id, provider_id, service_id, start_time, end_time, status) VALUES
(1, 1, 1, '2026-10-01T09:00:00', '2026-10-01T11:00:00', 'OPEN'),
(2, 1, 2, '2026-10-01T13:00:00', '2026-10-01T15:00:00', 'OPEN'),
(3, 1, 3, '2026-10-02T10:00:00', '2026-10-02T12:00:00', 'OPEN');
