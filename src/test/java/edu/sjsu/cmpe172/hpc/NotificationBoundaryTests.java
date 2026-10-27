package edu.sjsu.cmpe172.hpc;
import edu.sjsu.cmpe172.hpc.dto.BookingRequest;
import edu.sjsu.cmpe172.hpc.notification.*;
import edu.sjsu.cmpe172.hpc.service.BookingService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "hpc.notification.base-url=http://localhost:${local.server.port}")
class NotificationBoundaryTests {
    @Autowired BookingService bookings;
    @Autowired MockNotificationController stub;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeterRegistry meters;
    @SpyBean NotificationClient client;
    @BeforeEach void resetMutableData() {
        jdbc.update("DELETE FROM appointments");
        jdbc.update("DELETE FROM availability_slots WHERE id > 45");
        jdbc.update("UPDATE availability_slots SET status = 'OPEN', version = 0");
    }
    BookingRequest request() { return new BookingRequest(6L, 2L, 0L, "Training", "", "student@example.edu"); }
    double sentCount() {
        return meters.find("hpc.notifications").tag("outcome", "sent").counters().stream().mapToDouble(c -> c.count()).sum();
    }
    @Test void bookAndCancelSendConfirmationsAfterCommit() {
        doAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return inv.callRealMethod();
        }).when(client).send(any());
        double before = sentCount();
        var appointment = bookings.book(2, request());
        bookings.cancel(2, appointment.id());
        assertTrue(stub.received("appointment-" + appointment.id() + "-BOOKING_CONFIRMED"));
        assertTrue(stub.received("appointment-" + appointment.id() + "-BOOKING_CANCELLED"));
        assertEquals(before + 2, sentCount());
        int size = stub.size();
        bookings.cancel(2, appointment.id());
        assertEquals(size, stub.size());
    }
    @Test void duplicateIdempotencyKeyIsStoredOnce() {
        int before = stub.size();
        var request = new NotificationRequest("student@example.edu", NotificationType.REMINDER, "Reminder", 99, "dedupe-test");
        var first = http.postForEntity("/notification-service/v1/notifications", request, NotificationResponse.class);
        var second = http.postForEntity("/notification-service/v1/notifications", request, NotificationResponse.class);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals("ACCEPTED", first.getBody().status());
        assertEquals(HttpStatus.OK, second.getStatusCode());
        assertEquals("DUPLICATE", second.getBody().status());
        assertEquals(first.getBody().notificationId(), second.getBody().notificationId());
        assertEquals(before + 1, stub.size());
        var invalid = new NotificationRequest("invalid", null, "", 0, "");
        assertEquals(HttpStatus.BAD_REQUEST, http.postForEntity("/notification-service/v1/notifications", invalid, String.class).getStatusCode());
        assertEquals(before + 1, stub.size());
    }
    @Test void healthReportsDatabaseConnectivity() throws Exception {
        var response = http.getForEntity("/health", com.fasterxml.jackson.databind.JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().path("status").asText());
        assertEquals("UP", response.getBody().path("components").path("db").path("status").asText());
    }
    @Test void metricsEndpointExposesBookingCounter() {
        bookings.book(2, request());
        var response = http.getForEntity("/metrics/hpc.bookings", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode()); assertTrue(response.getBody().contains("COUNT"));
    }
}
