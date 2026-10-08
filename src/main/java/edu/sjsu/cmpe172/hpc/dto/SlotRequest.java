package edu.sjsu.cmpe172.hpc.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
public record SlotRequest(@NotNull @Positive Long serviceId, @NotNull LocalDateTime startTime,
                          @NotNull LocalDateTime endTime) { }
