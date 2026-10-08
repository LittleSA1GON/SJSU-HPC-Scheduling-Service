package edu.sjsu.cmpe172.hpc.security;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDate;
import edu.sjsu.cmpe172.hpc.repository.SlotRepository;
@Component
public class DemoAccountInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final Clock clock;
    public DemoAccountInitializer(JdbcTemplate jdbc, PasswordEncoder encoder, Clock clock) {
        this.jdbc = jdbc; this.encoder = encoder; this.clock = clock;
    }
    @Override public void run(ApplicationArguments args) {
        // Never overwrite an existing password.
        jdbc.update("UPDATE users SET password_hash = ? WHERE password_hash = 'DEMO_HASH_PENDING'",
                    encoder.encode("HpcDemo172!"));
        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        for (int day = 0; day < 3; day++) {
            for (int service = 0; service < 3; service++) {
                for (int time = 0; time < 5; time++) {
                    var start = tomorrow.plusDays(day).atTime(8 + time * 2, 0);
                    jdbc.update("""
                        INSERT OR IGNORE INTO availability_slots
                            (provider_id, service_id, start_time, end_time)
                        SELECT 1, ?, ?, ? WHERE NOT EXISTS (
                            SELECT 1 FROM availability_slots
                            WHERE provider_id = 1 AND service_id = ?
                              AND start_time < ? AND end_time > ?)
                        """, service + 1, SlotRepository.TIME.format(start),
                        SlotRepository.TIME.format(start.plusHours(2)), service + 1,
                        SlotRepository.TIME.format(start.plusHours(2)), SlotRepository.TIME.format(start));
                }
            }
        }
    }
}
