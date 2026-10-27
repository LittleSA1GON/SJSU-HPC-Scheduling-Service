package edu.sjsu.cmpe172.hpc.notification;
import java.time.Instant;
public record NotificationResponse(String notificationId, String status, Instant receivedAt) { }
