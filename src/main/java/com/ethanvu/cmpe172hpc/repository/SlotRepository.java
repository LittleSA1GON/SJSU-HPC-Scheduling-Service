package com.ethanvu.cmpe172hpc.repository;

import com.ethanvu.cmpe172hpc.dto.SlotDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SlotRepository {
    private final JdbcTemplate jdbcTemplate;

    public SlotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SlotDto> findAvailableSlots() {
        String sql = """
                SELECT
                    s.id AS slot_id,
                    p.id AS provider_id,
                    p.display_name AS provider_name,
                    sv.id AS service_id,
                    sv.name AS service_name,
                    sv.duration_minutes,
                    sv.price_cents,
                    sv.partition_hint,
                    sv.gpu_type,
                    s.start_time,
                    s.end_time,
                    s.status
                FROM availability_slots s
                JOIN providers p ON p.id = s.provider_id
                JOIN services sv ON sv.id = s.service_id
                LEFT JOIN appointments a
                    ON a.slot_id = s.id AND a.status = 'BOOKED'
                WHERE s.status = 'OPEN' AND a.id IS NULL
                ORDER BY s.start_time ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new SlotDto(
                rs.getLong("slot_id"),
                rs.getLong("provider_id"),
                rs.getString("provider_name"),
                rs.getLong("service_id"),
                rs.getString("service_name"),
                rs.getInt("duration_minutes"),
                rs.getInt("price_cents"),
                rs.getString("partition_hint"),
                rs.getString("gpu_type"),
                rs.getString("start_time"),
                rs.getString("end_time"),
                rs.getString("status")
        ));
    }
}
