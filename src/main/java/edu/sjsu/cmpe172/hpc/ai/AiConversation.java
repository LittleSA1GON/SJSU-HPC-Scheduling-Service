package edu.sjsu.cmpe172.hpc.ai;

import edu.sjsu.cmpe172.hpc.dto.BookingRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.Message;

/** Owned by one authenticated HTTP session; access is serialized by the controller. */
public class AiConversation {
    final long userId;
    final List<Message> messages = new ArrayList<>();
    Pending pending;
    public AiConversation(long userId) { this.userId = userId; }
    public record Pending(String token, String action, Object summary, BookingRequest booking,
                          Long appointmentId, Instant expiresAt) { }
    public record Proposal(String token, String action, Object summary, Instant expiresAt) { }
    public Proposal proposal() {
        return pending == null ? null : new Proposal(pending.token(), pending.action(), pending.summary(), pending.expiresAt());
    }
}
