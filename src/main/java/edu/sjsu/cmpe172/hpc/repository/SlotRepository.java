package edu.sjsu.cmpe172.hpc.repository;

import edu.sjsu.cmpe172.hpc.dto.PageDto;
import edu.sjsu.cmpe172.hpc.dto.SlotDto;
import edu.sjsu.cmpe172.hpc.dto.SlotRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SlotRepository {
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private final JdbcTemplate jdbc;
    public SlotRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String FROM = """
        FROM availability_slots s
        JOIN providers p ON p.provider_id = s.provider_id
        JOIN users u ON u.id = p.user_id
        JOIN services sv ON sv.id = s.service_id
        """;
    private static final String BOOKED = """
        EXISTS (SELECT 1 FROM appointments a WHERE a.slot_id = s.id AND a.status IN ('BOOKED', 'COMPLETED'))
        """;
    private static final String SELECT = """
        SELECT s.id AS slot_id, s.provider_id, u.display_name AS provider_name, s.service_id,
               sv.name AS service_name,
               CAST((julianday(s.end_time) - julianday(s.start_time)) * 1440 + 0.5 AS INTEGER) AS duration_minutes,
               sv.partition_hint, sv.gpu_type, s.start_time, s.end_time, s.version,
               CASE WHEN s.status = 'CLOSED' THEN 'CLOSED'
                    WHEN EXISTS (SELECT 1 FROM appointments a WHERE a.slot_id = s.id AND a.status IN ('BOOKED', 'COMPLETED'))
                    THEN 'BOOKED' ELSE 'OPEN' END AS display_status
        """;
    private static final RowMapper<SlotDto> MAPPER = (rs, row) -> new SlotDto(
        rs.getLong("slot_id"), rs.getLong("provider_id"), rs.getString("provider_name"),
        rs.getLong("service_id"), rs.getString("service_name"), rs.getInt("duration_minutes"),
        rs.getString("partition_hint"), rs.getString("gpu_type"), rs.getString("start_time"),
        rs.getString("end_time"), rs.getString("display_status"), rs.getLong("version"));

    public Optional<SlotDto> findById(long id) {
        return jdbc.query(SELECT + FROM + " WHERE s.id = ?", MAPPER, id).stream().findFirst();
    }
    public PageDto<SlotDto> browse(Long provider, Long service, LocalDate date, Integer duration,
                                   boolean availableOnly, boolean providerView, int page, int size, LocalDateTime now) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!providerView) {
            where.append(" AND s.status = 'OPEN' AND s.start_time > ?"); args.add(TIME.format(now));
        }
        if (provider != null) { where.append(" AND s.provider_id = ?"); args.add(provider); }
        if (service != null) { where.append(" AND s.service_id = ?"); args.add(service); }
        if (date != null) {
            where.append(" AND s.start_time >= ? AND s.start_time < ?");
            args.add(TIME.format(date.atStartOfDay())); args.add(TIME.format(date.plusDays(1).atStartOfDay()));
        }
        if (duration != null) {
            where.append(" AND CAST((julianday(s.end_time)-julianday(s.start_time))*1440+0.5 AS INTEGER) = ?"); args.add(duration);
        }
        if (availableOnly) where.append(" AND s.status = 'OPEN' AND NOT (").append(BOOKED).append(")");
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM + where, Long.class, args.toArray());
        args.add(size); args.add((long) page * size);
        List<SlotDto> items = jdbc.query(SELECT + FROM + where + " ORDER BY s.start_time, s.id LIMIT ? OFFSET ?", MAPPER, args.toArray());
        return PageDto.of(items, page, size, total == null ? 0 : total);
    }
    // Compare-and-swap: a stale version or occupied slot changes zero rows.
    public int claim(long id, long version, LocalDateTime now) {
        return jdbc.update("""
            UPDATE availability_slots SET version = version + 1
            WHERE id = ? AND version = ? AND status = 'OPEN' AND start_time > ?
              AND NOT EXISTS (SELECT 1 FROM appointments a
                  WHERE a.slot_id = availability_slots.id AND a.status IN ('BOOKED', 'COMPLETED'))
            """, id, version, TIME.format(now));
    }
    public void release(long id) {
        jdbc.update("UPDATE availability_slots SET version = version + 1 WHERE id = ?", id);
    }
    // Atomic insertion prevents overlapping provider slots even under concurrent requests.
    public Optional<SlotDto> create(long providerId, SlotRequest request) {
        String start = TIME.format(request.startTime()), end = TIME.format(request.endTime());
        int inserted = jdbc.update("""
            INSERT INTO availability_slots (provider_id, service_id, start_time, end_time)
            SELECT ?, ?, ?, ? WHERE NOT EXISTS (
                SELECT 1 FROM availability_slots
                WHERE provider_id = ? AND service_id = ? AND status = 'OPEN'
                  AND start_time < ? AND end_time > ?)
            """, providerId, request.serviceId(), start, end, providerId, request.serviceId(), end, start);
        if (inserted == 0) return Optional.empty();
        Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        return findById(id);
    }
    public int close(long id, long providerId, long version) {
        return jdbc.update("""
            UPDATE availability_slots SET status = 'CLOSED', version = version + 1
            WHERE id = ? AND provider_id = ? AND version = ? AND status = 'OPEN'
              AND NOT EXISTS (SELECT 1 FROM appointments a
                  WHERE a.slot_id = availability_slots.id AND a.status = 'BOOKED')
            """, id, providerId, version);
    }
}
