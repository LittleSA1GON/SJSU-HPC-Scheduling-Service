package edu.sjsu.cmpe172.hpc.controller;
import edu.sjsu.cmpe172.hpc.dto.*;
import edu.sjsu.cmpe172.hpc.security.HpcUser;
import edu.sjsu.cmpe172.hpc.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/provider")
@Validated
public class ProviderController {
    private final ProviderService providers;
    private final BookingService bookings;
    public ProviderController(ProviderService providers, BookingService bookings) { this.providers = providers; this.bookings = bookings; }
    @GetMapping("/slots")
    public PageDto<SlotDto> slots(@AuthenticationPrincipal HpcUser user,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return providers.list(user.id(), page, size);
    }
    @PostMapping("/slots")
    public ResponseEntity<SlotDto> create(@AuthenticationPrincipal HpcUser user, @Valid @RequestBody SlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(providers.create(user.id(), request));
    }
    @DeleteMapping("/slots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal HpcUser user, @PathVariable @Positive long id, @RequestParam @Min(0) long version) {
        providers.remove(user.id(), id, version);
    }
    @GetMapping("/appointments")
    public PageDto<AppointmentDto> appointments(@AuthenticationPrincipal HpcUser user,
        @RequestParam(defaultValue = "upcoming") String view,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "5") @Min(1) @Max(100) int size) {
        return bookings.list(providers.providerId(user.id()), true, view, page, size);
    }
}
