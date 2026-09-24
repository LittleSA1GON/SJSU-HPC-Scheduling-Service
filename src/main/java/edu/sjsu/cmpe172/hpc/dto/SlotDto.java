package edu.sjsu.cmpe172.hpc.dto;

/**
 * API response for one currently available app-level HPC reservation slot.
 * Controllers return DTOs rather than exposing database rows directly.
 */
public record SlotDto(
        long slotId,
        long providerId,
        String providerName,
        long serviceId,
        String serviceName,
        int durationMinutes,
        int priceCents,
        String partitionHint,
        String gpuType,
        String startTime,
        String endTime,
        String status
) {
}
