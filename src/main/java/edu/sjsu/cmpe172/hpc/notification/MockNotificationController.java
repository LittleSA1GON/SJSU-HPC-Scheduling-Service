package edu.sjsu.cmpe172.hpc.notification;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
// Stands in for an external messaging system; acceptance is stored only in memory.
@RestController
@RequestMapping("/notification-service/v1/notifications")
public class MockNotificationController {
    private static final Logger log = LoggerFactory.getLogger(MockNotificationController.class);
    private final ConcurrentHashMap<String, NotificationResponse> notifications = new ConcurrentHashMap<>();
    @PostMapping
    public ResponseEntity<NotificationResponse> accept(@Valid @RequestBody NotificationRequest request) {
        AtomicBoolean created = new AtomicBoolean();
        NotificationResponse response = notifications.computeIfAbsent(request.idempotencyKey(), key -> {
            created.set(true);
            return new NotificationResponse(UUID.randomUUID().toString(), "ACCEPTED", Instant.now());
        });
        if (created.get()) {
            log.info("Notification accepted id={} type={} appointmentId={}", response.notificationId(), request.type(), request.appointmentId());
            return ResponseEntity.status(201).body(response);
        }
        log.info("Notification duplicate ignored id={} type={} appointmentId={}", response.notificationId(), request.type(), request.appointmentId());
        return ResponseEntity.ok(new NotificationResponse(response.notificationId(), "DUPLICATE", response.receivedAt()));
    }
    public boolean received(String idempotencyKey) { return notifications.containsKey(idempotencyKey); }
    public int size() { return notifications.size(); }
}
