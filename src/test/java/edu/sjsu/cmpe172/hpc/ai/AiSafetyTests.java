package edu.sjsu.cmpe172.hpc.ai;

import edu.sjsu.cmpe172.hpc.dto.BookingRequest;
import edu.sjsu.cmpe172.hpc.exception.ApiException;
import edu.sjsu.cmpe172.hpc.notification.*;
import edu.sjsu.cmpe172.hpc.repository.*;
import edu.sjsu.cmpe172.hpc.service.*;
import jakarta.validation.Validator;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/ai-safety-${random.uuid}.db?busy_timeout=3000")
@AutoConfigureMockMvc
class AiSafetyTests {
    @Autowired BookingService bookings;
    @Autowired BookingTransactionService transactions;
    @Autowired SlotService slots;
    @Autowired CatalogService catalog;
    @Autowired UserRepository users;
    @Autowired AppointmentRepository appointments;
    @Autowired Validator validator;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockBean NotificationClient notifications;

    @BeforeEach void reset() {
        jdbc.update("DELETE FROM appointments");
        jdbc.update("DELETE FROM availability_slots WHERE id > 45");
        jdbc.update("UPDATE availability_slots SET status='OPEN', version=0");
        when(notifications.send(any())).thenReturn(true);
    }
    SchedulingTools tools(String username, AiConversation conversation) {
        return new SchedulingTools(users.findByUsername(username).orElseThrow(), conversation, bookings, slots, catalog, validator, clock);
    }
    @Test void agentBookUsesRealTransactionAndRejectsDoubleBooking() {
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(transactions));
        var first = new AiConversation(2);
        var second = new AiConversation(3);
        var alice = tools("student-researcher", first);
        var bob = tools("second-researcher", second);
        alice.book(6, "Model training", "A100");
        bob.book(6, "Other training", "A100");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM appointments", Integer.class));
        var booked = alice.confirm(first.proposal().token());
        assertEquals(2, booked.customerId());
        assertEquals("BOOKED", booked.status());
        assertEquals(409, assertThrows(ApiException.class, () -> bob.confirm(second.proposal().token())).status().value());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM appointments", Integer.class));
        assertNull(second.proposal());
    }
    @Test void confirmationIsBoundToSessionSingleUseAndExpires() {
        var state = new AiConversation(2);
        var tool = tools("student-researcher", state);
        tool.book(6, "Training", "");
        String token = state.proposal().token();
        assertThrows(ApiException.class, () -> tool.confirm("wrong-token"));
        assertThrows(ApiException.class, () -> tools("second-researcher", new AiConversation(3)).confirm(token));
        var expiredTool = new SchedulingTools(users.findByUsername("student-researcher").orElseThrow(), state,
            bookings, slots, catalog, validator, Clock.offset(clock, Duration.ofMinutes(6)));
        assertThrows(ApiException.class, () -> expiredTool.confirm(token));
        tool.confirm(token);
        assertThrows(ApiException.class, () -> tool.confirm(token));
    }
    @Test void toolsRejectWrongRoleAndCrossUserCancellation() {
        assertThrows(ApiException.class, () -> tools("hpc-admin", new AiConversation(1)));
        assertThrows(ApiException.class, () -> tools("student-researcher", new AiConversation(3)));
        var booked = bookings.book(2, new BookingRequest(6L, 2L, 0L, "Training", "", "student@example.edu"));
        assertEquals(403, assertThrows(ApiException.class,
            () -> tools("second-researcher", new AiConversation(3)).cancel(booked.id())).status().value());
        var state = new AiConversation(2);
        var tool = tools("student-researcher", state);
        tool.cancel(booked.id());
        assertEquals("BOOKED", bookings.get(2, booked.id()).status());
        assertEquals("CANCELLED", tool.confirm(state.proposal().token()).status());
    }
    @Test void aiEndpointsRequireRoleCsrfAndReturnDisabledStatus() throws Exception {
        mvc.perform(post("/api/ai/chat").with(csrf()).contentType("application/json").content("{\"message\":\"hello\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ai/chat").with(user("admin").roles("PROVIDER_ADMIN")).with(csrf())
            .contentType("application/json").content("{\"message\":\"hello\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/confirm").with(user("customer").roles("CUSTOMER"))
            .contentType("application/json").content("{\"token\":\"fake\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/chat").with(user(users.findByUsername("student-researcher").orElseThrow())).with(csrf())
            .contentType("application/json").content("{\"message\":\"hello\"}")).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/ai/reminders/run").with(user("customer").roles("CUSTOMER")).with(csrf())).andExpect(status().isForbidden());
    }
    ChatModel model() {
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Prepare your training dataset.")))));
        return model;
    }
    long upcomingBooking() {
        var start = LocalDateTime.now(clock).plusHours(2);
        jdbc.update("UPDATE availability_slots SET start_time=?, end_time=? WHERE id=6", start.toString(), start.plusHours(2).toString());
        return bookings.book(2, new BookingRequest(6L, 2L, 0L, "Training", "", "student@example.edu")).id();
    }
    @Test void autonomousAgentGeneratesSendsAndDeduplicatesAcrossInstances() {
        long id = upcomingBooking();
        var model = model();
        var agent = new ReminderAgent(jdbc, appointments, ChatClient.create(model), notifications, clock);
        assertEquals(0, agent.sweep(2L).scanned()); // Other provider cannot trigger this reminder.
        assertEquals(1, agent.sweep(1L).sent());
        verify(notifications).send(argThat(n -> n.type() == NotificationType.REMINDER && n.appointmentId() == id
            && n.message().contains("Prepare your training dataset.") && n.idempotencyKey().equals("appointment-" + id + "-REMINDER")));
        assertEquals(0, new ReminderAgent(jdbc, appointments, ChatClient.create(model), notifications, clock).sweep(null).scanned());
        verify(model, times(1)).call(any(Prompt.class));
    }
    @Test void reminderFailureRetriesLaterAndCancellationIsExcluded() {
        long id = upcomingBooking();
        var agent = new ReminderAgent(jdbc, appointments, ChatClient.create(model()), notifications, clock);
        when(notifications.send(argThat(n -> n != null && n.type() == NotificationType.REMINDER))).thenReturn(false);
        assertEquals(1, agent.sweep(null).failed());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_reminder_receipts", Integer.class));
        when(notifications.send(any())).thenReturn(true);
        assertEquals(1, agent.sweep(null).sent());
        jdbc.update("DELETE FROM ai_reminder_receipts");
        bookings.cancel(2, id);
        assertEquals(0, agent.sweep(null).scanned());
    }
    @Test void modelOutageDoesNotSendOrMarkReminder() {
        upcomingBooking();
        var model = model();
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("unavailable"));
        var result = new ReminderAgent(jdbc, appointments, ChatClient.create(model), notifications, clock).sweep(null);
        assertEquals(1, result.failed());
        verify(notifications, never()).send(argThat(n -> n.type() == NotificationType.REMINDER));
    }
}
