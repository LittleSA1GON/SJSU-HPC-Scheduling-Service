package edu.sjsu.cmpe172.hpc.controller;
import edu.sjsu.cmpe172.hpc.dto.*;
import edu.sjsu.cmpe172.hpc.service.SlotService;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController
@Validated
public class SlotController {
    private final SlotService slots;
    public SlotController(SlotService slots) { this.slots = slots; }
    @GetMapping("/api/slots")
    public PageDto<SlotDto> availableSlots(
        @RequestParam(required = false) @Positive Long providerId,
        @RequestParam(required = false) @Positive Long serviceId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) @Positive Integer durationMinutes,
        @RequestParam(defaultValue = "true") boolean availableOnly,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "5") @Min(1) @Max(100) int size) {
        return slots.browse(providerId, serviceId, date, durationMinutes, availableOnly, page, size);
    }
}
