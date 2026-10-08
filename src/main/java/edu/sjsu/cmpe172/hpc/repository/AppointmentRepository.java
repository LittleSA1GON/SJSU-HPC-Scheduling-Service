package edu.sjsu.cmpe172.hpc.repository;

import edu.sjsu.cmpe172.hpc.dto.AppointmentDto;
import edu.sjsu.cmpe172.hpc.dto.BookingRequest;
import edu.sjsu.cmpe172.hpc.dto.PageDto;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AppointmentRepository {
    private final JdbcTemplate jdbc;
    public AppointmentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String FROM = """
        FROM appointments a JOIN availability_slots s ON s.id = a.slot_id
        JOIN services sv ON sv.id = a.service_id JOIN providers p ON p.provider_id = s.provider_id
        JOIN users pu ON pu.id = p.user_id JOIN users cu ON cu.id = a.customer_id
        """;
    private static final String SELECT = """
        SELECT a.*, s.provider_id, pu.display_name AS provider_name, cu.display_name AS customer_name,
               sv.name AS service_name, sv.gpu_type, s.start_time, s.end_time,
               CAST((julianday(s.end_time)-julianday(s.start_time))*1440+0.5 AS INTEGER) AS duration_minutes,
               CASE WHEN a.status = 'BOOKED' AND s.end_time <= ? THEN 'COMPLETED' ELSE a.status END AS effective_status
        """;
    private static final RowMapper<AppointmentDto> MAPPER = (rs, row) -> new AppointmentDto(
        rs.getLong("id"), rs.getLong("customer_id"), rs.getString("customer_name"), rs.getLong("slot_id"),
        rs.getLong("provider_id"), rs.getString("provider_name"), rs.getLong("service_id"),
        rs.getString("service_name"), rs.getString("gpu_type"), rs.getString("start_time"),
        rs.getString("end_time"), rs.getInt("duration_minutes"), rs.getString("effective_status"),
        rs.getString("project_name"), rs.getString("resource_requirements"), rs.getString("contact_email"), rs.getString("created_at"));

    public Optional<AppointmentDto> findById(long id, LocalDateTime now) {
        return jdbc.query(SELECT + FROM + " WHERE a.id = ?", MAPPER, SlotRepository.TIME.format(now), id).stream().findFirst();
    }
    public AppointmentDto insert(long customerId, BookingRequest request, LocalDateTime now) {
        jdbc.update("""
            INSERT INTO appointments (customer_id, slot_id, service_id, project_name, resource_requirements, contact_email)
            VALUES (?, ?, ?, ?, ?, ?)
            """, customerId, request.slotId(), request.serviceId(), request.projectName().trim(),
            request.resourceRequirements() == null ? "" : request.resourceRequirements().trim(), request.contactEmail().trim());
        Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        return findById(id, now).orElseThrow();
    }
    public int cancel(long id, long customerId, LocalDateTime now) {
        return jdbc.update("""
            UPDATE appointments SET status = 'CANCELLED'
            WHERE id = ? AND customer_id = ? AND status = 'BOOKED'
              AND EXISTS (SELECT 1 FROM availability_slots s WHERE s.id = appointments.slot_id AND s.start_time > ?)
            """, id, customerId, SlotRepository.TIME.format(now));
    }
    public PageDto<AppointmentDto> list(long ownerId, boolean provider, String view, int page, int size, LocalDateTime now) {
        String where = provider ? " WHERE s.provider_id = ?" : " WHERE a.customer_id = ?";
        String time = SlotRepository.TIME.format(now);
        List<Object> args = new ArrayList<>(); args.add(ownerId);
        if ("upcoming".equals(view)) { where += " AND a.status = 'BOOKED' AND s.end_time > ?"; args.add(time); }
        else if ("history".equals(view)) { where += " AND (a.status != 'BOOKED' OR s.end_time <= ?)"; args.add(time); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM + where, Long.class, args.toArray());
        args.add(0, time); args.add(size); args.add((long) page * size);
        String order = "upcoming".equals(view) ? " ASC" : " DESC";
        List<AppointmentDto> items = jdbc.query(SELECT + FROM + where +
            " ORDER BY s.start_time" + order + ", a.id" + order + " LIMIT ? OFFSET ?", MAPPER, args.toArray());
        return PageDto.of(items, page, size, total == null ? 0 : total);
    }
    public int completePast(LocalDateTime now) {
        return jdbc.update("""
            UPDATE appointments SET status = 'COMPLETED'
            WHERE status = 'BOOKED' AND slot_id IN (SELECT id FROM availability_slots WHERE end_time <= ?)
            """, SlotRepository.TIME.format(now));
    }
}
