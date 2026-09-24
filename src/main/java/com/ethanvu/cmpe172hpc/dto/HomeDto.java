package com.ethanvu.cmpe172hpc.dto;

public record HomeDto(
        String application,
        String scenario,
        int providerCount,
        int serviceCount,
        int availableSlotCount
) {
}
