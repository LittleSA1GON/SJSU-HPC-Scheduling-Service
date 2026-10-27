package edu.sjsu.cmpe172.hpc.notification;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
@Component
public class NotificationClient {
    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);
    private final RestClient client;
    private final Environment environment;
    private final MeterRegistry meters;
    private final int maxAttempts;
    private final Duration backoff;
    public NotificationClient(RestClient.Builder builder, Environment environment, MeterRegistry meters,
            @Value("${hpc.notification.connect-timeout:500ms}") Duration connectTimeout,
            @Value("${hpc.notification.read-timeout:1s}") Duration readTimeout,
            @Value("${hpc.notification.max-attempts:3}") int maxAttempts,
            @Value("${hpc.notification.backoff:200ms}") Duration backoff) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout); factory.setReadTimeout(readTimeout);
        this.client = builder.requestFactory(factory).build();
        this.environment = environment; this.meters = meters;
        this.maxAttempts = Math.max(1, maxAttempts); this.backoff = backoff;
    }
    public boolean send(NotificationRequest request) {
        int attempts = 0;
        try {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                attempts = attempt;
                try {
                    String baseUrl = environment.getProperty("hpc.notification.base-url");
                    client.post().uri(baseUrl + "/notification-service/v1/notifications")
                        .header("Idempotency-Key", request.idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().toBodilessEntity();
                    count(request, "sent");
                    log.info("Notification sent type={} appointmentId={} attempt={}", request.type(), request.appointmentId(), attempt);
                    return true;
                } catch (RuntimeException ex) {
                    // Do not log exception messages: remote response bodies may contain recipients.
                    log.warn("Notification attempt {}/{} failed type={} appointmentId={}: {}", attempt, maxAttempts,
                        request.type(), request.appointmentId(), ex.getClass().getSimpleName());
                    if (ex instanceof HttpClientErrorException) break;
                    if (attempt < maxAttempts) {
                        try { Thread.sleep(backoff.multipliedBy(attempt).toMillis()); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Notification delivery failed: {}", ex.getClass().getSimpleName());
        }
        try { count(request, "failed"); }
        catch (RuntimeException ex) { log.warn("Notification failure metric unavailable: {}", ex.getClass().getSimpleName()); }
        log.warn("Notification fallback: {} for appointment {} not delivered after {} attempt(s); booking unaffected",
            request == null ? null : request.type(), request == null ? null : request.appointmentId(), attempts);
        return false;
    }
    private void count(NotificationRequest request, String outcome) {
        meters.counter("hpc.notifications", "type", request.type().name(), "outcome", outcome).increment();
    }
}
