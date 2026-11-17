package edu.sjsu.cmpe172.hpc.ai;

import edu.sjsu.cmpe172.hpc.dto.*;
import edu.sjsu.cmpe172.hpc.exception.ApiException;
import edu.sjsu.cmpe172.hpc.security.HpcUser;
import edu.sjsu.cmpe172.hpc.service.*;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** A new instance is bound to the server-authenticated customer on every turn.
 * The LLM can propose writes but cannot execute or confirm them. */
public class SchedulingTools {
    private final HpcUser user;
    private final AiConversation conversation;
    private final BookingService bookings;
    private final SlotService slots;
    private final CatalogService catalog;
    private final Validator validator;
    private final Clock clock;

    public SchedulingTools(HpcUser user, AiConversation conversation, BookingService bookings,
            SlotService slots, CatalogService catalog, Validator validator, Clock clock) {
        if (!"CUSTOMER".equals(user.role()) || user.id() != conversation.userId)
            throw ApiException.forbidden("Only the signed-in customer can use scheduling tools");
        this.user = user; this.conversation = conversation; this.bookings = bookings;
        this.slots = slots; this.catalog = catalog; this.validator = validator; this.clock = clock;
    }

    @Tool(description = "List actual service IDs, providers, today's date and the scheduler time zone.")
    public CatalogDto catalog() { return catalog.getCatalog(); }

    @Tool(description = "Find future available slots in chronological order. Date is optional YYYY-MM-DD; page starts at zero. Check multiple dates for a week. Never invent slot IDs or versions.")
    public PageDto<SlotDto> checkAvailability(@ToolParam(required = false) Long serviceId,
            @ToolParam(required = false) LocalDate date, int page) {
        if (page < 0 || page > 1000) throw ApiException.badRequest("Invalid page");
        return slots.browse(null, serviceId, date, null, true, page, 20);
    }

    @Tool(description = "List only the logged-in customer's appointments. View: upcoming, history, all. Page starts at zero.")
    public PageDto<AppointmentDto> listMyAppointments(String view, int page) {
        if (page < 0 || page > 1000) throw ApiException.badRequest("Invalid page");
        return bookings.list(user.id(), false, view, page, 20);
    }

    @Tool(description = "Propose a booking for user review; does NOT book. Collect project name and requirements from the user. Uses the account email. A separate user confirmation button commits the exact proposal.")
    public Object book(long slotId, String projectName, @ToolParam(required = false) String resourceRequirements) {
        var slot = slots.available(slotId);
        var request = new BookingRequest(slotId, slot.serviceId(), slot.version(), projectName, resourceRequirements, user.email());
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        conversation.pending = new AiConversation.Pending(UUID.randomUUID().toString(), "BOOK",
            Map.of("slot", slot, "projectName", projectName, "resourceRequirements", resourceRequirements == null ? "" : resourceRequirements,
                   "contactEmail", user.email()), request, null, clock.instant().plusSeconds(300));
        return Map.of("status", "AWAITING_USER_CONFIRMATION", "summary", conversation.pending.summary());
    }

    @Tool(description = "Propose cancelling the logged-in customer's reservation. Does NOT cancel. The user must confirm with the separate confirmation button.")
    public Object cancel(long appointmentId) {
        var appointment = bookings.get(user.id(), appointmentId);
        if (!"BOOKED".equals(appointment.status()) || !LocalDateTime.parse(appointment.startTime()).isAfter(LocalDateTime.now(clock)))
            throw ApiException.conflict("Only future booked reservations can be cancelled");
        conversation.pending = new AiConversation.Pending(UUID.randomUUID().toString(), "CANCEL", appointment,
            null, appointmentId, clock.instant().plusSeconds(300));
        return Map.of("status", "AWAITING_USER_CONFIRMATION", "summary", appointment);
    }

    // Deliberately NOT a model tool. Only the authenticated, CSRF-protected confirmation endpoint calls it.
    public AppointmentDto confirm(String token) {
        var pending = conversation.pending;
        if (pending == null || !pending.token().equals(token) || !pending.expiresAt().isAfter(clock.instant()))
            throw ApiException.conflict("Proposal expired or already used. Ask the assistant for a new proposal.");
        conversation.pending = null; // Consume before execution; no replay, including after a conflict.
        return "BOOK".equals(pending.action()) ? bookings.book(user.id(), pending.booking())
            : bookings.cancel(user.id(), pending.appointmentId());
    }
}
