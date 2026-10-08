package edu.sjsu.cmpe172.hpc.repository;
import edu.sjsu.cmpe172.hpc.dto.CatalogDto.*;
import edu.sjsu.cmpe172.hpc.exception.ApiException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class CatalogRepository {
    private final JdbcTemplate jdbc;
    public CatalogRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<ServiceDto> services() {
        return jdbc.query("SELECT * FROM services ORDER BY id", (rs, row) -> new ServiceDto(
            rs.getLong("id"), rs.getString("name"), rs.getString("description"),
            rs.getInt("duration_minutes"), rs.getString("partition_hint"), rs.getString("gpu_type")));
    }
    public List<ProviderDto> providers() {
        return jdbc.query("""
            SELECT p.provider_id, u.display_name FROM providers p
            JOIN users u ON u.id = p.user_id ORDER BY p.provider_id
            """, (rs, row) -> new ProviderDto(rs.getLong("provider_id"), rs.getString("display_name")));
    }
    public long providerIdForUser(long userId) {
        return jdbc.query("SELECT provider_id FROM providers WHERE user_id = ?",
            (rs, row) -> rs.getLong(1), userId).stream().findFirst()
            .orElseThrow(() -> ApiException.notFound("Provider profile not found"));
    }
    public ServiceDto service(long id) {
        return services().stream().filter(s -> s.id() == id).findFirst()
            .orElseThrow(() -> ApiException.notFound("Service not found"));
    }
}
