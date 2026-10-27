package edu.sjsu.cmpe172.hpc.notification;
import edu.sjsu.cmpe172.hpc.dto.AppointmentDto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
public record NotificationRequest(
    @NotBlank @Email @Size(max = 254) String recipient,
    @NotNull NotificationType type,
    @NotBlank @Size(max = 500) String message,
    @Positive long appointmentId,
    @NotBlank @Size(max = 100) String idempotencyKey) {
    public static NotificationRequest forAppointment(NotificationType type, AppointmentDto a) {
        String action = switch (type) {
            case BOOKING_CONFIRMED -> "is confirmed";
            case BOOKING_CANCELLED -> "is cancelled";
            case REMINDER -> "is coming up";
        };
        String start = LocalDateTime.parse(a.startTime()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String end = LocalDateTime.parse(a.endTime()).format(DateTimeFormatter.ofPattern("HH:mm"));
        String message = "Reservation #" + a.id() + " for " + a.serviceName() + " on " + start + "-" + end
            + " (Pacific Time) " + action + ".";
        return new NotificationRequest(a.contactEmail(), type, message, a.id(), "appointment-" + a.id() + "-" + type);
    }
}
