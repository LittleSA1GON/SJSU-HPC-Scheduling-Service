package com.ethanvu.cmpe172hpc.external;

/**
 * Required course distribution boundary for the later booking/cancellation workflow.
 * Milestone 1 only defines the boundary; Milestone 3 will use it after database commit.
 */
public interface NotificationGateway {
    void sendConfirmation(String destination, String message);
}
