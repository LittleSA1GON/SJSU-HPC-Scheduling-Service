package edu.sjsu.cmpe172.hpc;

import edu.sjsu.cmpe172.hpc.dto.*;
import edu.sjsu.cmpe172.hpc.exception.ApiException;
import edu.sjsu.cmpe172.hpc.repository.*;
import edu.sjsu.cmpe172.hpc.service.*;
import edu.sjsu.cmpe172.hpc.notification.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.ArgumentCaptor;
import jakarta.validation.*;
import java.sql.SQLException;
import java.time.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.UncategorizedSQLException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingServiceTests {
    SlotRepository slots;
    AppointmentRepository appointments;
    BookingTransactionService transactions;
    final Clock clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneId.of("America/Los_Angeles"));
    final BookingRequest request = new BookingRequest(6L, 2L, 0L, "Training", "", "student@example.edu");
    @BeforeEach void setup() {
        slots = mock(SlotRepository.class); appointments = mock(AppointmentRepository.class);
        transactions = new BookingTransactionService(slots, appointments, clock);
    }
    SlotDto slot(String status, String start) {
        return new SlotDto(6, 1, "Admin", 2, "GPU Compute", 120, "gpu", "A100", start, "2030-01-02T11:00:00", status, 0);
    }
    AppointmentDto appointment(long owner, String status) {
        return new AppointmentDto(10, owner, "Researcher", 6, 1, "Admin", 2, "GPU Compute", "A100",
            "2030-01-02T09:00:00", "2030-01-02T11:00:00", 120, status, "Training", "", "student@example.edu", "");
    }
    @Test void unknownSlotIs404() {
        when(slots.findById(6)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ApiException.class, () -> transactions.book(2, request)).status().value());
        verifyNoInteractions(appointments);
    }
    @Test void rejectsWrongServiceBeforeClaim() {
        when(slots.findById(6)).thenReturn(Optional.of(slot("OPEN", "2030-01-02T09:00:00")));
        assertEquals(400, assertThrows(ApiException.class, () -> transactions.book(2,
            new BookingRequest(6L, 1L, 0L, "Training", "", "student@example.edu"))).status().value());
        verify(slots, never()).claim(anyLong(), anyLong(), any());
    }
    @Test void rejectsPastAndBookedSlots() {
        when(slots.findById(6)).thenReturn(Optional.of(slot("OPEN", "2020-01-02T09:00:00")));
        assertEquals(409, assertThrows(ApiException.class, () -> transactions.book(2, request)).status().value());
        when(slots.findById(6)).thenReturn(Optional.of(slot("BOOKED", "2030-01-02T09:00:00")));
        assertEquals(409, assertThrows(ApiException.class, () -> transactions.book(2, request)).status().value());
        verifyNoInteractions(appointments);
    }
    @Test void failedOptimisticClaimDoesNotInsert() {
        when(slots.findById(6)).thenReturn(Optional.of(slot("OPEN", "2030-01-02T09:00:00")));
        when(slots.claim(eq(6L), eq(0L), any())).thenReturn(0);
        assertEquals(409, assertThrows(ApiException.class, () -> transactions.book(2, request)).status().value());
        verifyNoInteractions(appointments);
    }
    @Test void successfulClaimInsertsForAuthenticatedCustomer() {
        when(slots.findById(6)).thenReturn(Optional.of(slot("OPEN", "2030-01-02T09:00:00")));
        when(slots.claim(eq(6L), eq(0L), any())).thenReturn(1);
        when(appointments.insert(eq(2L), eq(request), any())).thenReturn(appointment(2, "BOOKED"));
        assertEquals(2, transactions.book(2, request).customerId());
    }
    @Test void cancellationRequiresOwnerEvenWhenAlreadyCancelled() {
        when(appointments.findById(eq(10L), any())).thenReturn(Optional.of(appointment(3, "CANCELLED")));
        assertEquals(403, assertThrows(ApiException.class, () -> transactions.cancel(2, 10)).status().value());
        verify(appointments, never()).cancel(anyLong(), anyLong(), any()); verifyNoInteractions(slots);
    }
    @Test void completedReservationCannotBeCancelled() {
        when(appointments.findById(eq(10L), any())).thenReturn(Optional.of(appointment(2, "COMPLETED")));
        assertEquals(409, assertThrows(ApiException.class, () -> transactions.cancel(2, 10)).status().value());
        verifyNoInteractions(slots);
    }
    @Test void alreadyCancelledReservationIsIdempotent() {
        when(appointments.findById(eq(10L), any())).thenReturn(Optional.of(appointment(2, "CANCELLED")));
        assertEquals("CANCELLED", transactions.cancel(2, 10).status()); verifyNoInteractions(slots);
    }
    @Test void cancellationReleasesSlot() {
        when(appointments.findById(eq(10L), any())).thenReturn(Optional.of(appointment(2, "BOOKED")), Optional.of(appointment(2, "CANCELLED")));
        when(appointments.cancel(eq(10L), eq(2L), any())).thenReturn(1);
        assertEquals("CANCELLED", transactions.cancel(2, 10).status()); verify(slots).release(6);
    }
    @Test void serviceValidatesInputsBeforeEnteringTransaction() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            BookingTransactionService worker = mock(BookingTransactionService.class);
            BookingService service = new BookingService(worker, appointments, new SqliteRetry(), clock, factory.getValidator(),
                mock(NotificationClient.class), new SimpleMeterRegistry());
            assertThrows(ConstraintViolationException.class, () -> service.book(2,
                new BookingRequest(0L, 2L, -1L, " ", "x".repeat(201), "not-email")));
            verifyNoInteractions(worker);
        }
    }
    @Test void busySnapshotRetriesWholeTransactionButLogicalConflictsDoNotRetry() {
        SqliteRetry retry = new SqliteRetry(); AtomicInteger calls = new AtomicInteger();
        assertEquals("success", retry.execute(() -> {
            if (calls.incrementAndGet() == 1) throw new UncategorizedSQLException("test", "", new SQLException("busy snapshot", "", 517));
            return "success";
        }));
        assertEquals(2, calls.get()); calls.set(0);
        assertThrows(ApiException.class, () -> retry.execute(() -> { calls.incrementAndGet(); throw ApiException.conflict("Occupied"); }));
        assertEquals(1, calls.get());
    }
    @Test void retryIsBoundedAndReturns503() {
        AtomicInteger calls = new AtomicInteger();
        ApiException error = assertThrows(ApiException.class, () -> new SqliteRetry().execute(() -> {
            calls.incrementAndGet(); throw new UncategorizedSQLException("test", "", new SQLException("busy", "", 5));
        }));
        assertEquals(3, calls.get()); assertEquals(503, error.status().value());
    }
    @Test void notifiesAfterCommitAndBookingSurvivesNotificationFailure() {
        var meters = new SimpleMeterRegistry();
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var worker = mock(BookingTransactionService.class);
            var client = mock(NotificationClient.class);
            var expected = appointment(2, "BOOKED");
            when(worker.book(2, request)).thenReturn(expected);
            when(client.send(any())).thenReturn(false);
            var service = new BookingService(worker, appointments, new SqliteRetry(), clock, factory.getValidator(), client, meters);
            assertEquals(expected, service.book(2, request));
            var order = inOrder(worker, client);
            order.verify(worker).book(2, request);
            var sent = ArgumentCaptor.forClass(NotificationRequest.class);
            order.verify(client).send(sent.capture());
            assertEquals(NotificationType.BOOKING_CONFIRMED, sent.getValue().type());
            assertEquals("appointment-10-BOOKING_CONFIRMED", sent.getValue().idempotencyKey());
            assertEquals(1, meters.get("hpc.bookings").tag("outcome", "created").counter().count());
        } finally { meters.close(); }
    }
}
