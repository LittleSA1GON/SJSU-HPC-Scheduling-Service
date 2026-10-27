package edu.sjsu.cmpe172.hpc;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.cmpe172.hpc.dto.BookingRequest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties = {"hpc.notification.base-url=http://127.0.0.1:9", "hpc.notification.backoff=20ms"})
@AutoConfigureMockMvc
class NotificationFailureTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired MeterRegistry meters;
    @BeforeEach void resetMutableData() {
        jdbc.update("DELETE FROM appointments");
        jdbc.update("DELETE FROM availability_slots WHERE id > 45");
        jdbc.update("UPDATE availability_slots SET status = 'OPEN', version = 0");
    }
    @Test void bookingStillSucceedsWhenNotificationServiceIsDown() throws Exception {
        var response = mvc.perform(post("/api/appointments").with(user(HpcApplicationTests.CUSTOMER)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                new BookingRequest(6L, 2L, 0L, "Training", "", "student@example.edu"))))
            .andExpect(status().isCreated()).andReturn();
        long id = json.readTree(response.getResponse().getContentAsString()).get("id").asLong();
        assertEquals("BOOKED", jdbc.queryForObject("SELECT status FROM appointments WHERE id = ?", String.class, id));
        assertEquals(1, meters.get("hpc.notifications").tags("type", "BOOKING_CONFIRMED", "outcome", "failed").counter().count());
        assertEquals(1, meters.get("hpc.bookings").tag("outcome", "created").counter().count());
    }
}
