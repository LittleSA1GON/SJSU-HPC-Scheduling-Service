package edu.sjsu.cmpe172.hpc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HpcApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetMutableData() {
        jdbcTemplate.update("DELETE FROM appointments");
        jdbcTemplate.update("UPDATE availability_slots SET status = 'OPEN'");
    }

    @Test
    void foreignKeysAreEnabledForPooledConnection() {
        Integer enabled = jdbcTemplate.queryForObject("PRAGMA foreign_keys", Integer.class);
        assertEquals(1, enabled);
    }

    @Test
    void seedDataHasExpectedCounts() {
        assertEquals(2, count("users"));
        assertEquals(1, count("providers"));
        assertEquals(3, count("services"));
        assertEquals(3, count("availability_slots"));
        assertEquals(0, count("appointments"));
    }

    @Test
    void homeEndpointReadsDatabaseAndReturnsDto() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("SJSU HPC Resource Reservation Portal"))
                .andExpect(jsonPath("$.providerCount").value(1))
                .andExpect(jsonPath("$.serviceCount").value(3))
                .andExpect(jsonPath("$.availableSlotCount").value(3));
    }

    @Test
    void slotsEndpointReadsDatabaseAndReturnsThreeDtoRecords() throws Exception {
        mockMvc.perform(get("/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].serviceName").value("CPU Batch Compute"))
                .andExpect(jsonPath("$[0].partitionHint").value("compute"))
                .andExpect(jsonPath("$[1].gpuType").value("A100"))
                .andExpect(jsonPath("$[2].gpuType").value("H100"))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void bookedSlotIsExcludedFromAvailableSlotsEndpoint() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO appointments (id, customer_id, slot_id, service_id, status)
                VALUES (1, 2, 1, 1, 'BOOKED')
                """);

        mockMvc.perform(get("/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void databaseRejectsDoubleBookingSameSlot() {
        jdbcTemplate.update("""
                INSERT INTO appointments (id, customer_id, slot_id, service_id, status)
                VALUES (1, 2, 1, 1, 'BOOKED')
                """);

        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                        INSERT INTO appointments (id, customer_id, slot_id, service_id, status)
                        VALUES (2, 2, 1, 1, 'BOOKED')
                        """));
    }

    @Test
    void databaseRejectsUnknownCustomerForeignKey() {
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                        INSERT INTO appointments (id, customer_id, slot_id, service_id, status)
                        VALUES (1, 999, 1, 1, 'BOOKED')
                        """));
    }

    private int count(String tableName) {
        Integer result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return result == null ? 0 : result;
    }
}
