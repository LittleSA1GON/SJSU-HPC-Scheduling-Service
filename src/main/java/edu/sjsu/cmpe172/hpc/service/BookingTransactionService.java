package edu.sjsu.cmpe172.hpc.service;
import edu.sjsu.cmpe172.hpc.dto.*;
import edu.sjsu.cmpe172.hpc.exception.ApiException;
import edu.sjsu.cmpe172.hpc.repository.*;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
@Service
public class BookingTransactionService {
    private final SlotRepository slots;
    private final AppointmentRepository appointments;
    private final Clock clock;
    public BookingTransactionService(SlotRepository slots, AppointmentRepository appointments, Clock clock) {
        this.slots = slots; this.appointments = appointments; this.clock = clock;
    }
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AppointmentDto book(long customerId, BookingRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        SlotDto slot = slots.findById(request.slotId()).orElseThrow(() -> ApiException.notFound("Slot not found"));
        if (slot.serviceId() != request.serviceId()) throw ApiException.badRequest("The service does not match this slot");
        if (!LocalDateTime.parse(slot.startTime()).isAfter(now)) throw ApiException.conflict("This slot has already started");
        if (!"OPEN".equals(slot.status())) throw ApiException.conflict("This slot is no longer available");
        if (slots.claim(slot.slotId(), request.version(), now) != 1)
            throw ApiException.conflict("This slot has changed or was just booked. Refresh the available slots.");
        // INSERT failure rolls back the claim/version increment.
        return appointments.insert(customerId, request, now);
    }
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AppointmentDto cancel(long customerId, long appointmentId) {
        LocalDateTime now = LocalDateTime.now(clock);
        AppointmentDto appointment = appointments.findById(appointmentId, now)
            .orElseThrow(() -> ApiException.notFound("Reservation not found"));
        if (appointment.customerId() != customerId) throw ApiException.forbidden("Only the reservation owner can cancel it");
        if ("CANCELLED".equals(appointment.status())) return appointment;
        if (!"BOOKED".equals(appointment.status()) || !LocalDateTime.parse(appointment.startTime()).isAfter(now))
            throw ApiException.conflict("A reservation that has started or completed cannot be cancelled");
        if (appointments.cancel(appointmentId, customerId, now) != 1) throw ApiException.conflict("Reservation has changed");
        slots.release(appointment.slotId()); // Invalidate browser versions captured before cancellation.
        return appointments.findById(appointmentId, now).orElseThrow();
    }
}
