-- Simulated reservations only; demo BCrypt hashes are initialized on first startup.
INSERT OR IGNORE INTO users (id, username, email, password_hash, display_name, role) VALUES
(1, 'hpc-admin', 'hpc-admin@example.edu', 'DEMO_HASH_PENDING', 'HPC Admin', 'PROVIDER_ADMIN'),
(2, 'student-researcher', 'student@example.edu', 'DEMO_HASH_PENDING', 'Ethan', 'CUSTOMER'),
(3, 'second-researcher', 'researcher@example.edu', 'DEMO_HASH_PENDING', 'Alex', 'CUSTOMER'),
(4, 'lab-admin', 'lab-admin@example.edu', 'DEMO_HASH_PENDING', 'Research Lab Admin', 'PROVIDER_ADMIN');
INSERT OR IGNORE INTO providers (provider_id, user_id, description) VALUES
(1, 1, 'SJSU HPC simulated compute resources.'),
(2, 4, 'Research laboratory simulated compute resources.');
INSERT OR IGNORE INTO services (id, name, description, duration_minutes, partition_hint, gpu_type) VALUES
(1, 'CPU Compute', 'General purpose compute nodes', 120, 'compute', NULL),
(2, 'GPU Compute', 'GPU-accelerated compute nodes', 120, 'gpu', '4 x A100'),
(3, 'Storage Access', 'High-performance storage resources', 120, 'storage', NULL);
