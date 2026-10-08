package edu.sjsu.cmpe172.hpc.dto;
import java.time.Instant;
public record ApiError(Instant timestamp, int status, String code, String message, String path) { }
