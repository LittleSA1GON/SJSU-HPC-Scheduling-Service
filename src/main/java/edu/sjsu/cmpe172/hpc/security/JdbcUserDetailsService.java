package edu.sjsu.cmpe172.hpc.security;
import edu.sjsu.cmpe172.hpc.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
@Service
public class JdbcUserDetailsService implements UserDetailsService {
    private final UserRepository users;
    public JdbcUserDetailsService(UserRepository users) { this.users = users; }
    @Override public UserDetails loadUserByUsername(String username) {
        return users.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));
    }
}
