package edu.sjsu.cmpe172.hpc.repository;
import edu.sjsu.cmpe172.hpc.security.HpcUser;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;
    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<HpcUser> findByUsername(String username) {
        return jdbc.query("SELECT * FROM users WHERE username = ?", (rs, row) -> new HpcUser(
            rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
            rs.getString("display_name"), rs.getString("email"), rs.getString("role")
        ), username).stream().findFirst();
    }
}
