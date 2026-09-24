package edu.sjsu.cmpe172.hpc.dto;

public record HomeDto(
        String application,
        String scenario,
        int providerCount,
        int serviceCount,
        int availableSlotCount
) {
}
