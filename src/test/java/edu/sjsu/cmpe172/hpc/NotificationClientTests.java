package edu.sjsu.cmpe172.hpc;
import com.sun.net.httpserver.HttpServer;
import edu.sjsu.cmpe172.hpc.notification.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
class NotificationClientTests {
    HttpServer server;
    ExecutorService executor;
    SimpleMeterRegistry meters;
    NotificationClient client;
    AtomicInteger calls;
    final NotificationRequest request = new NotificationRequest("student@example.edu", NotificationType.BOOKING_CONFIRMED,
        "Reservation confirmed", 10, "appointment-10-BOOKING_CONFIRMED");
    @BeforeEach void setup() throws Exception {
        calls = new AtomicInteger(); meters = new SimpleMeterRegistry();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool(); server.setExecutor(executor);
        server.start();
        var environment = new MockEnvironment().withProperty("hpc.notification.base-url", "http://127.0.0.1:" + server.getAddress().getPort());
        client = new NotificationClient(RestClient.builder(), environment, meters,
            Duration.ofMillis(300), Duration.ofMillis(300), 3, Duration.ofMillis(10));
    }
    void respond(int status, boolean slow) {
        server.createContext("/notification-service/v1/notifications", exchange -> {
            calls.incrementAndGet();
            assertEquals(request.idempotencyKey(), exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            assertTrue(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .contains("student@example.edu"));
            try {
                if (slow) Thread.sleep(2000);
                exchange.sendResponseHeaders(status, -1);
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
    }
    @AfterEach void cleanup() throws Exception {
        server.stop(0); executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); meters.close();
    }
    @Test void deliversOnceOnSuccess() {
        respond(201, false); assertTrue(client.send(request)); assertEquals(1, calls.get());
        assertEquals(1, meters.get("hpc.notifications").tags("type", request.type().name(), "outcome", "sent").counter().count());
    }
    @Test void retriesServerErrorsThenFallsBack() {
        respond(503, false); assertFalse(client.send(request)); assertEquals(3, calls.get());
        assertEquals(1, meters.get("hpc.notifications").tags("type", request.type().name(), "outcome", "failed").counter().count());
    }
    @Test void timesOutSlowServiceThenFallsBack() {
        respond(201, true); long start = System.nanoTime();
        assertFalse(client.send(request));
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 2500);
        assertEquals(3, calls.get());
    }
    @Test void doesNotRetryClientErrors() {
        respond(400, false); assertFalse(client.send(request)); assertEquals(1, calls.get());
    }
}
