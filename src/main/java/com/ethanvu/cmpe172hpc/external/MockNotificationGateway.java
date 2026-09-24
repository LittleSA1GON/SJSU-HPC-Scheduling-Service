package com.ethanvu.cmpe172hpc.external;

import org.springframework.stereotype.Component;

/**
 * No-op Milestone 1 implementation of the required mock external notification service.
 */
@Component
public class MockNotificationGateway implements NotificationGateway {
    @Override
    public void sendConfirmation(String destination, String message) {
        // Intentionally no-op in Milestone 1. Booking/cancellation is implemented later.
    }
}
