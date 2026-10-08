package edu.sjsu.cmpe172.hpc.dto;
public record AppointmentDto(long id, long customerId, String customerName, long slotId,
    long providerId, String providerName, long serviceId, String serviceName, String gpuType,
    String startTime, String endTime, int durationMinutes, String status, String projectName,
    String resourceRequirements, String contactEmail, String createdAt) { }
