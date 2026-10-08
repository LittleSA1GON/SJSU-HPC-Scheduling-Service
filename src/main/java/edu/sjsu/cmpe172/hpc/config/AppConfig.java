package edu.sjsu.cmpe172.hpc.config;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SQLExceptionSubclassTranslator;
import org.springframework.dao.DataIntegrityViolationException;
@Configuration
@EnableScheduling
public class AppConfig {
    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        SQLExceptionSubclassTranslator fallback = new SQLExceptionSubclassTranslator();
        jdbc.setExceptionTranslator((task, sql, error) -> {
            // SQLite does not provide the SQLState that Spring's generic translator expects.
            if ((error.getErrorCode() & 0xff) == 19)
                return new DataIntegrityViolationException("Database constraint rejected the operation", error);
            return fallback.translate(task, sql, error);
        });
        return jdbc;
    }
    @Bean
    Clock clock(@Value("${hpc.time-zone:America/Los_Angeles}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
