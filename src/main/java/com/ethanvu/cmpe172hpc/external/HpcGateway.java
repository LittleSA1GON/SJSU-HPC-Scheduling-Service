package com.ethanvu.cmpe172hpc.external;

/**
 * Boundary for future Slurm/HPC integration.
 * Milestone 1 intentionally uses a mock implementation and does not require cluster access.
 */
public interface HpcGateway {
    String getClusterStatus();
}
