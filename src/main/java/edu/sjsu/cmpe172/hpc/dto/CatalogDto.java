package edu.sjsu.cmpe172.hpc.dto;
import java.util.List;
public record CatalogDto(List<ServiceDto> services, List<ProviderDto> providers, String timeZone, String today) {
    public record ServiceDto(long id, String name, String description, int durationMinutes, String partitionHint, String gpuType) { }
    public record ProviderDto(long id, String name) { }
}
