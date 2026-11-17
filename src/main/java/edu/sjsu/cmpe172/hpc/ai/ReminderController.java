package edu.sjsu.cmpe172.hpc.ai;

import edu.sjsu.cmpe172.hpc.exception.ApiException;
import edu.sjsu.cmpe172.hpc.security.HpcUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReminderController {
    private final ObjectProvider<ReminderAgent> agents;
    private final JdbcTemplate jdbc;
    public ReminderController(ObjectProvider<ReminderAgent> agents, JdbcTemplate jdbc) { this.agents = agents; this.jdbc = jdbc; }
    @PostMapping("/api/ai/reminders/run")
    public ReminderAgent.Result run(@AuthenticationPrincipal HpcUser user) {
        var agent = agents.getIfAvailable();
        if (agent == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Enable the ai profile to run reminders");
        Long provider = jdbc.queryForObject("SELECT provider_id FROM providers WHERE user_id = ?", Long.class, user.id());
        return agent.sweep(provider);
    }
}
