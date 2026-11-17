package edu.sjsu.cmpe172.hpc.ai;

import edu.sjsu.cmpe172.hpc.dto.AppointmentDto;
import edu.sjsu.cmpe172.hpc.exception.ApiException;
import edu.sjsu.cmpe172.hpc.security.HpcUser;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final ObjectProvider<ConversationalAgent> agents;
    public AiController(ObjectProvider<ConversationalAgent> agents) { this.agents = agents; }
    public record ChatRequest(@NotBlank @Size(max = 2000) String message) { }
    public record Confirmation(@NotBlank @Size(max = 50) String token) { }
    private ConversationalAgent agent() {
        var agent = agents.getIfAvailable();
        if (agent == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI is disabled. Enable the ai profile and configure GEMINI_API_KEY.");
        return agent;
    }
    private AiConversation conversation(HttpSession session, HpcUser user) {
        var current = (AiConversation) session.getAttribute("hpc.ai");
        if (current == null || current.userId != user.id()) {
            current = new AiConversation(user.id()); session.setAttribute("hpc.ai", current);
        }
        return current;
    }
    @PostMapping("/chat")
    public ConversationalAgent.Reply chat(@AuthenticationPrincipal HpcUser user, HttpSession session,
            @Valid @RequestBody ChatRequest request) {
        synchronized (session) {
            var conversation = conversation(session, user);
            try { return agent().respond(user, conversation, request.message()); }
            catch (ApiException ex) { throw ex; }
            catch (RuntimeException ex) {
                conversation.pending = null;
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI could not complete this turn. Retry later or use Available Slots.");
            }
        }
    }
    @PostMapping("/confirm")
    public AppointmentDto confirm(@AuthenticationPrincipal HpcUser user, HttpSession session,
            @Valid @RequestBody Confirmation request) {
        synchronized (session) {
            var conversation = conversation(session, user);
            var appointment = agent().tools(user, conversation).confirm(request.token());
            conversation.messages.add(new org.springframework.ai.chat.messages.AssistantMessage(
                "Server-confirmed write result: reservation #" + appointment.id() + " is " + appointment.status()));
            return appointment;
        }
    }
    @DeleteMapping("/chat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(HttpSession session) { synchronized (session) { session.removeAttribute("hpc.ai"); } }
}
