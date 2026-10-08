package edu.sjsu.cmpe172.hpc.dto;
import jakarta.validation.constraints.*;
public record BookingRequest(
    @NotNull @Positive Long slotId, @NotNull @Positive Long serviceId, @NotNull @Min(0) Long version,
    @NotBlank @Size(max = 120) String projectName, @Size(max = 200) String resourceRequirements,
    @NotBlank @Email @Size(max = 254) String contactEmail
) { }
