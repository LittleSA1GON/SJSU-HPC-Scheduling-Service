package edu.sjsu.cmpe172.hpc.dto;
import java.util.List;
public record PageDto<T>(List<T> items, int page, int size, long total, long totalPages) {
    public static <T> PageDto<T> of(List<T> items, int page, int size, long total) {
        return new PageDto<>(items, page, size, total, (total + size - 1) / size);
    }
}
