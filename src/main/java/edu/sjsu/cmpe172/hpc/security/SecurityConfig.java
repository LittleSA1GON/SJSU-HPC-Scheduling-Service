package edu.sjsu.cmpe172.hpc.security;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.cmpe172.hpc.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper json) throws Exception {
        // Session-backed CSRF and session-fixation protection remain enabled.
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/error",
                             "/api/auth/csrf", "/api/auth/me", "/api/catalog", "/api/slots").permitAll()
            .requestMatchers("/api/provider/**").hasRole("PROVIDER_ADMIN")
            .requestMatchers("/api/appointments/**").hasRole("CUSTOMER")
            .anyRequest().authenticated())
            .formLogin(login -> login.loginProcessingUrl("/api/auth/login")
                .successHandler((req, res, auth) -> res.setStatus(204))
                .failureHandler((req, res, ex) -> writeError(json, req, res, 401,
                    "UNAUTHORIZED", "Invalid username or password")))
            .logout(logout -> logout.logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req, res, ex) -> writeError(json, req, res, 401,
                    "UNAUTHORIZED", "Please sign in to continue"))
                .accessDeniedHandler((req, res, ex) -> writeError(json, req, res, 403,
                    ex instanceof CsrfException ? "INVALID_CSRF" : "FORBIDDEN",
                    ex instanceof CsrfException ? "Your security token has expired. Please try again." : "This role cannot perform that action")));
        return http.build();
    }
    private static void writeError(ObjectMapper json, HttpServletRequest req,
                                   HttpServletResponse res, int status, String code, String message)
            throws IOException {
        res.setStatus(status); res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(res.getOutputStream(), new ApiError(Instant.now(), status, code, message, req.getRequestURI()));
    }
}
