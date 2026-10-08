package edu.sjsu.cmpe172.hpc.service;
import edu.sjsu.cmpe172.hpc.dto.*;
import edu.sjsu.cmpe172.hpc.exception.ApiException;
import edu.sjsu.cmpe172.hpc.repository.AppointmentRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
@Service
public class BookingService {
    private final BookingTransactionService transactions;
    private final AppointmentRepository appointments;
    private final SqliteRetry retry;
    private final Clock clock;
    private final Validator validator;
    public BookingService(BookingTransactionService transactions, AppointmentRepository appointments,
                          SqliteRetry retry, Clock clock, Validator validator) {
        this.transactions = transactions; this.appointments = appointments;
        this.retry = retry; this.clock = clock; this.validator = validator;
    }
    public AppointmentDto book(long customerId, BookingRequest request) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        try { return retry.execute(() -> transactions.book(customerId, request)); }
        catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("The slot could not be reserved. Refresh the available slots.");
        }
    }
    public AppointmentDto cancel(long customerId, long appointmentId) {
        return retry.execute(() -> transactions.cancel(customerId, appointmentId));
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
