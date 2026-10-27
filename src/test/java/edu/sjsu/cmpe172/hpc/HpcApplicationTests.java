package edu.sjsu.cmpe172.hpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.cmpe172.hpc.dto.*;
import edu.sjsu.cmpe172.hpc.repository.*;
import edu.sjsu.cmpe172.hpc.security.HpcUser;
import edu.sjsu.cmpe172.hpc.security.DemoAccountInitializer;
import edu.sjsu.cmpe172.hpc.service.*;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class HpcApplicationTests {
    @org.springframework.boot.test.mock.mockito.MockBean
    edu.sjsu.cmpe172.hpc.notification.NotificationClient notifications;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired BookingService bookings;
    @Autowired SlotRepository slots;
    @Autowired AppointmentMaintenance maintenance;
    @Autowired PasswordEncoder encoder;
    @Autowired Clock clock;

    static final HpcUser CUSTOMER = new HpcUser(2, "student-researcher", "", "Ethan", "student@example.edu", "CUSTOMER");
    static final HpcUser OTHER = new HpcUser(3, "second-researcher", "", "Alex", "researcher@example.edu", "CUSTOMER");
    static final HpcUser PROVIDER = new HpcUser(1, "hpc-admin", "", "HPC Admin", "hpc-admin@example.edu", "PROVIDER_ADMIN");
    static final HpcUser LAB = new HpcUser(4, "lab-admin", "", "Lab Admin", "lab-admin@example.edu", "PROVIDER_ADMIN");

    @BeforeEach
    void resetMutableData() {
        jdbc.update("DELETE FROM appointments");
        jdbc.update("DELETE FROM availability_slots WHERE id > 45");
        jdbc.update("UPDATE availability_slots SET status = 'OPEN', version = 0");
    }
    BookingRequest request(long slot, long service, long version) {
        return new BookingRequest(slot, service, version, "Model training", "Test workload", "student@example.edu");
    }
    @Test void seedAndConnectionConfiguration() {
        assertEquals(4, count("users")); assertEquals(2, count("providers"));
        assertEquals(3, count("services")); assertEquals(45, count("availability_slots"));
        assertEquals(1, jdbc.queryForObject("PRAGMA foreign_keys", Integer.class));
        assertEquals("wal", jdbc.queryForObject("PRAGMA journal_mode", String.class));
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = 2", String.class);
        assertTrue(hash.startsWith("$2a$")); assertTrue(encoder.matches("HpcDemo172!", hash));
    }
    @Test void laterStartupAddsFutureAvailabilityAndPreservesExistingRecords() {
        AppointmentDto booked = bookings.book(2, request(6, 2, 0));
        jdbc.update("UPDATE availability_slots SET status = 'CLOSED', version = 1 WHERE id = 1");
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = 2", String.class);
        // Restarting today must not reopen a removed slot or reset a booked version.
        new DemoAccountInitializer(jdbc, encoder, clock).run(null);
        assertEquals(45, count("availability_slots"));
        assertEquals("CLOSED", slots.findById(1).orElseThrow().status());
        assertEquals(1, slots.findById(6).orElseThrow().version());
        Clock later = Clock.fixed(LocalDate.now(clock).plusDays(4)
            .atStartOfDay(clock.getZone()).toInstant(), clock.getZone());
        var initializer = new DemoAccountInitializer(jdbc, encoder, later);
        initializer.run(null);
        initializer.run(null);
        assertEquals(90, count("availability_slots"));
        assertEquals(45, slots.browse(null, null, null, null, true, false, 0, 100,
            LocalDateTime.now(later)).total());
        assertEquals(booked.id(), bookings.get(2, booked.id()).id());
        assertEquals(hash, jdbc.queryForObject("SELECT password_hash FROM users WHERE id = 2", String.class));
    }
    @Test void rendersInterfaceForAnonymousVisitor() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isNoContent());
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("SJSU HPC")));
    }
    @Test void sqlFiltersAndPagination() throws Exception {
        String tomorrow = LocalDate.now(clock).plusDays(1).toString();
        mvc.perform(get("/api/slots").param("serviceId", "2").param("providerId", "1")
            .param("date", tomorrow).param("size", "2").param("page", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[0].slotId").value(8));
        mvc.perform(get("/api/slots").param("durationMinutes", "60"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/slots").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/slots").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/slots").param("date", "not-a-date")).andExpect(status().isBadRequest());
    }
    @Test void realLoginCreatesSessionReadsJdbcRoleAndLogoutInvalidatesIt() throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf())
            .param("username", "student-researcher").param("password", "HpcDemo172!"))
            .andExpect(status().isNoContent()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(2)).andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "student-researcher").param("password", "bad"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "missing").param("password", "bad"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid username or password"));
    }
    @Test void rbacAndCsrfAreEnforcedOnServer() throws Exception {
        mvc.perform(get("/api/appointments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/provider/slots").with(user(CUSTOMER))).andExpect(status().isForbidden());
        mvc.perform(get("/api/appointments").with(user(PROVIDER))).andExpect(status().isForbidden());
        mvc.perform(post("/api/appointments").with(user(PROVIDER)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(6, 2, 0))))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/appointments").with(user(CUSTOMER))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(6, 2, 0))))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_CSRF"));
    }
    @Test void bookingCancellationHistoryAndRebooking() throws Exception {
        AppointmentDto first = bookings.book(2, request(6, 2, 0));
        mvc.perform(get("/api/appointments/" + first.id()).with(user(CUSTOMER)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BOOKED"));
        mvc.perform(get("/api/slots").param("serviceId", "2")).andExpect(jsonPath("$.total").value(14));
        mvc.perform(get("/api/slots").param("serviceId", "2").param("availableOnly", "false"))
            .andExpect(jsonPath("$.total").value(15));
        mvc.perform(post("/api/appointments/" + first.id() + "/cancel").with(user(OTHER)).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/appointments/" + first.id()).with(user(OTHER))).andExpect(status().isForbidden());
        mvc.perform(post("/api/appointments/" + first.id() + "/cancel").with(user(CUSTOMER)).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals(2, slots.findById(6).orElseThrow().version());
        mvc.perform(post("/api/appointments/" + first.id() + "/cancel").with(user(CUSTOMER)).with(csrf()))
            .andExpect(status().isOk());
        assertEquals(2, slots.findById(6).orElseThrow().version());
        assertThrows(edu.sjsu.cmpe172.hpc.exception.ApiException.class, () -> bookings.book(3, request(6, 2, 0)));
        AppointmentDto second = bookings.book(3, request(6, 2, 2));
        assertNotEquals(first.id(), second.id()); assertEquals(2, count("appointments"));
        mvc.perform(get("/api/appointments").with(user(CUSTOMER)).param("view", "history"))
            .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].status").value("CANCELLED"));
        mvc.perform(get("/api/provider/appointments").with(user(PROVIDER)))
            .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].customerId").value(3));
        mvc.perform(get("/api/provider/appointments").with(user(LAB))).andExpect(jsonPath("$.total").value(0));
    }
    @Test void invalidRequestsUseStandardErrors() throws Exception {
        mvc.perform(post("/api/appointments").with(user(CUSTOMER)).with(csrf())
            .contentType(MediaType.TEXT_PLAIN).content("slotId=6"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.status").value(415))
            .andExpect(jsonPath("$.trace").doesNotExist());
        mvc.perform(post("/api/appointments").with(user(CUSTOMER)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"slotId":6,"serviceId":2,"version":0,"projectName":" ","contactEmail":"invalid"}
                """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(post("/api/appointments").with(user(CUSTOMER)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(999, 2, 0))))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(post("/api/appointments").with(user(CUSTOMER)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(6, 1, 0))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.trace").doesNotExist());
        mvc.perform(post("/api/appointments").with(user(CUSTOMER)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(6, 2, 9))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        mvc.perform(get("/api/appointments").with(user(CUSTOMER)).param("view", "invalid")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/provider/slots/6").with(user(PROVIDER)).with(csrf()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.trace").doesNotExist());
    }
    @Test void providerCreationOverlapOwnershipAndRemoval() throws Exception {
        LocalDateTime start = LocalDate.now(clock).plusDays(10).atTime(9, 0);
        String body = json.writeValueAsString(new SlotRequest(1L, start, start.plusHours(2)));
        var result = mvc.perform(post("/api/provider/slots").with(user(LAB)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.providerId").value(2)).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).get("slotId").asLong();
        mvc.perform(post("/api/provider/slots").with(user(LAB)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(delete("/api/provider/slots/" + id).param("version", "0").with(user(PROVIDER)).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/provider/slots/" + id).param("version", "99").with(user(LAB)).with(csrf()))
            .andExpect(status().isConflict());
        bookings.book(2, request(id, 1, 0));
        mvc.perform(delete("/api/provider/slots/" + id).param("version", "1").with(user(LAB)).with(csrf()))
            .andExpect(status().isConflict());
        long appointmentId = jdbc.queryForObject("SELECT id FROM appointments WHERE slot_id = ?", Long.class, id);
        bookings.cancel(2, appointmentId);
        mvc.perform(delete("/api/provider/slots/" + id).param("version", "2").with(user(LAB)).with(csrf()))
            .andExpect(status().isNoContent());
        assertEquals("CLOSED", slots.findById(id).orElseThrow().status());
        assertEquals(1, count("appointments"));
        mvc.perform(post("/api/provider/slots").with(user(LAB)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new SlotRequest(1L, start, start.minusHours(1)))))
            .andExpect(status().isBadRequest());
    }
    @Test void transactionRollsBackVersionWhenAppointmentInsertFails() {
        assertThrows(edu.sjsu.cmpe172.hpc.exception.ApiException.class, () -> bookings.book(999, request(6, 2, 0)));
        assertEquals(0, slots.findById(6).orElseThrow().version()); assertEquals(0, count("appointments"));
        assertNotNull(bookings.book(2, request(6, 2, 0)));
    }
    @Test void uniqueSlotBackstopRejectsDirectDoubleBooking() {
        bookings.book(2, request(6, 2, 0));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
            INSERT INTO appointments (customer_id, slot_id, service_id, project_name, contact_email)
            VALUES (3, 6, 2, 'Duplicate', 'researcher@example.edu')
            """));
        assertEquals(1, count("appointments"));
    }
    @Test void pastReservationsAreCompletedAndCannotBeCancelled() throws Exception {
        jdbc.update("""
            INSERT INTO availability_slots (id, provider_id, service_id, start_time, end_time)
            VALUES (100, 1, 1, '2020-01-01T09:00:00', '2020-01-01T11:00:00')
            """);
        jdbc.update("""
            INSERT INTO appointments (customer_id, slot_id, service_id, project_name, contact_email)
            VALUES (2, 100, 1, 'Past run', 'student@example.edu')
            """);
        mvc.perform(get("/api/appointments").with(user(CUSTOMER)).param("view", "history"))
            .andExpect(jsonPath("$.items[0].status").value("COMPLETED"));
        maintenance.completePastReservations();
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM appointments WHERE slot_id=100", String.class));
        long id = jdbc.queryForObject("SELECT id FROM appointments WHERE slot_id=100", Long.class);
        mvc.perform(post("/api/appointments/" + id + "/cancel").with(user(CUSTOMER)).with(csrf())).andExpect(status().isConflict());
        mvc.perform(post("/api/appointments").with(user(CUSTOMER)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(request(100, 1, 0)))).andExpect(status().isConflict());
    }
    @Test
    void twoConcurrentHttpBookingsProduceExactlyOne201AndOne409() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        String body = json.writeValueAsString(request(6, 2, 0));
        try {
            List<Future<Integer>> attempts = List.of(CUSTOMER, OTHER).stream().map(customer -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
                return mvc.perform(post("/api/appointments").with(user(customer)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            List<Integer> statuses = List.of(attempts.get(0).get(10, TimeUnit.SECONDS), attempts.get(1).get(10, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(status -> status == 201).count(), statuses.toString());
            assertEquals(1, statuses.stream().filter(status -> status == 409).count(), statuses.toString());
            assertEquals(1, count("appointments")); assertEquals(1, slots.findById(6).orElseThrow().version());
        } finally { start.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
}
