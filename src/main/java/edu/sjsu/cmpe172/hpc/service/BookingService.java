package edu.sjsu.cmpe172.hpc.service;
import edu.sjsu.cmpe172.hpc.dto.*;
import edu.sjsu.cmpe172.hpc.exception.ApiException;
import edu.sjsu.cmpe172.hpc.repository.AppointmentRepository;
import edu.sjsu.cmpe172.hpc.notification.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
@Service
public class BookingService {
    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private final NotificationClient notifications;
    private final MeterRegistry meters;
    private final BookingTransactionService transactions;
    private final AppointmentRepository appointments;
    private final SqliteRetry retry;
    private final Clock clock;
    private final Validator validator;
    public BookingService(BookingTransactionService transactions, AppointmentRepository appointments,
                          SqliteRetry retry, Clock clock, Validator validator, NotificationClient notifications, MeterRegistry meters) {
        this.transactions = transactions; this.appointments = appointments;
        this.retry = retry; this.clock = clock; this.validator = validator;
        this.notifications = notifications; this.meters = meters;
    }
    public AppointmentDto book(long customerId, BookingRequest request) {
        Timer.Sample sample = Timer.start(meters);
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        AppointmentDto appointment;
        try { appointment = retry.execute(() -> transactions.book(customerId, request)); }
        catch (DataIntegrityViolationException ex) {
            rejected(request, 409, "The slot could not be reserved. Refresh the available slots.");
            throw ApiException.conflict("The slot could not be reserved. Refresh the available slots.");
        }
        catch (ApiException ex) {
            rejected(request, ex.status().value(), ex.getMessage());
            throw ex;
        }
        sample.stop(meters.timer("hpc.booking.latency"));
        meters.counter("hpc.bookings", "outcome", "created").increment();
        log.info("Booking created appointmentId={} slotId={} customerId={}", appointment.id(), appointment.slotId(), customerId);
        // After commit: never inside the database transaction.
        notifications.send(NotificationRequest.forAppointment(NotificationType.BOOKING_CONFIRMED, appointment));
        return appointment;
    }
    private void rejected(BookingRequest request, int status, String message) {
        meters.counter("hpc.bookings", "outcome", "rejected").increment();
        log.warn("Booking rejected slotId={} status={}: {}", request.slotId(), status, message);
    }
    public AppointmentDto cancel(long customerId, long appointmentId) {
        AppointmentDto appointment = retry.execute(() -> transactions.cancel(customerId, appointmentId));
        log.info("Booking cancelled appointmentId={}", appointmentId);
        // After commit: never inside the database transaction.
        notifications.send(NotificationRequest.forAppointment(NotificationType.BOOKING_CANCELLED, appointment));
        return appointment;
    }
    public AppointmentDto get(long customerId, long id) {
        AppointmentDto appointment = appointments.findById(id, LocalDateTime.now(clock))
            .orElseThrow(() -> ApiException.notFound("Reservation not found"));
        if (appointment.customerId() != customerId) throw ApiException.forbidden("This reservation belongs to another customer");
        return appointment;
    }
    public PageDto<AppointmentDto> list(long ownerId, boolean provider, String view, int page, int size) {
        if (!java.util.Set.of("all", "upcoming", "history").contains(view))
            throw ApiException.badRequest("View must be all, upcoming, or history");
        return appointments.list(ownerId, provider, view, page, size, LocalDateTime.now(clock));
    }
}
