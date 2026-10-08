package edu.sjsu.cmpe172.hpc.controller;
import edu.sjsu.cmpe172.hpc.dto.*;
import edu.sjsu.cmpe172.hpc.security.HpcUser;
import edu.sjsu.cmpe172.hpc.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/appointments")
@Validated
public class AppointmentController {
    private final BookingService bookings;
    public AppointmentController(BookingService bookings) { this.bookings = bookings; }
    @PostMapping
    public ResponseEntity<AppointmentDto> book(@AuthenticationPrincipal HpcUser user, @Valid @RequestBody BookingRequest request) {
        AppointmentDto appointment = bookings.book(user.id(), request);
        return ResponseEntity.created(java.net.URI.create("/api/appointments/" + appointment.id())).body(appointment);
    }
    @GetMapping
    public PageDto<AppointmentDto> mine(@AuthenticationPrincipal HpcUser user,
        @RequestParam(defaultValue = "upcoming") String view,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "5") @Min(1) @Max(100) int size) {
        return bookings.list(user.id(), false, view, page, size);
    }
    @GetMapping("/{id}")
    public AppointmentDto get(@AuthenticationPrincipal HpcUser user, @PathVariable @Positive long id) {
        return bookings.get(user.id(), id);
    }
    @PostMapping("/{id}/cancel")
    public AppointmentDto cancel(@AuthenticationPrincipal HpcUser user, @PathVariable @Positive long id) {
        return bookings.cancel(user.id(), id);
    }
}
