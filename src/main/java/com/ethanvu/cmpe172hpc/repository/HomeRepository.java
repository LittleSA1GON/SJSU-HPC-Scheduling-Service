package com.ethanvu.cmpe172hpc.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HomeRepository {
    private final JdbcTemplate jdbcTemplate;

    public HomeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int countProviders() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM providers", Integer.class);
        return count == null ? 0 : count;
    }

    public int countServices() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM services", Integer.class);
        return count == null ? 0 : count;
    }

    public int countAvailableSlots() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM availability_slots s
                LEFT JOIN appointments a
                    ON a.slot_id = s.id AND a.status = 'BOOKED'
                WHERE s.status = 'OPEN' AND a.id IS NULL
                """,
                Integer.class
        );
        return count == null ? 0 : count;
    }
}
