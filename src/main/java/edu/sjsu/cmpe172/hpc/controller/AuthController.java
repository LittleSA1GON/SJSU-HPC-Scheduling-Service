package edu.sjsu.cmpe172.hpc.controller;
import edu.sjsu.cmpe172.hpc.security.HpcUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
@RestController
public class AuthController {
    @GetMapping("/api/auth/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal HpcUser user) {
        if (user == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(new UserDto(user.id(), user.username(), user.displayName(), user.email(), user.role()));
    }
    @GetMapping("/api/auth/csrf")
    public CsrfDto csrf(CsrfToken token) { return new CsrfDto(token.getHeaderName(), token.getToken()); }
    public record UserDto(long id, String username, String displayName, String email, String role) { }
    public record CsrfDto(String headerName, String token) { }
}
