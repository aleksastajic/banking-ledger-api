package com.aleksastajic.ledger.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LedgerFlowIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createEntry_isIdempotent_andUpdatesBalances() throws Exception {
        UUID customer = createAccount("Alice", "CUSTOMER");
        UUID internal = createAccount("Ops", "INTERNAL");

                fund(customer, internal, "fund-1", "10.0000");

        String payload = "{" +
                "\"description\":\"payment\"," +
                "\"postings\":[" +
                "{\"accountId\":\"" + customer + "\",\"currency\":\"EUR\",\"amount\":\"-10.0000\"}," +
                "{\"accountId\":\"" + internal + "\",\"currency\":\"EUR\",\"amount\":\"10.0000\"}" +
                "]}";

        JsonNode first = createJournalEntry("11111111-1111-1111-1111-111111111111", "idem-1", payload);
        JsonNode second = createJournalEntry("11111111-1111-1111-1111-111111111111", "idem-1", payload);

        assertThat(first.get("id").asText()).isEqualTo(second.get("id").asText());
        assertThat(jdbc.queryForObject("select count(*) from journal_entries where description = 'payment'", Integer.class)).isEqualTo(1);

        mockMvc.perform(get("/accounts/{id}/balances", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[0].currency").value("EUR"))
                .andExpect(jsonPath("$.balances[0].balance").value("0.0000"));
    }

    @Test
    void reversal_isLinked_andSecondReversalConflicts() throws Exception {
        UUID customer = createAccount("Alice", "CUSTOMER");
        UUID internal = createAccount("Ops", "INTERNAL");

                fund(customer, internal, "fund-2", "10.0000");

        String payload = "{" +
                "\"description\":\"payment\"," +
                "\"postings\":[" +
                "{\"accountId\":\"" + customer + "\",\"currency\":\"EUR\",\"amount\":\"-10.0000\"}," +
                "{\"accountId\":\"" + internal + "\",\"currency\":\"EUR\",\"amount\":\"10.0000\"}" +
                "]}";

        JsonNode original = createJournalEntry("11111111-1111-1111-1111-111111111111", "idem-2", payload);
        UUID originalId = UUID.fromString(original.get("id").asText());

        String reversePayload = "{\"description\":\"refund\"}";

        JsonNode reversal = objectMapper.readTree(mockMvc.perform(post("/journal-entries/{id}/reversal", originalId)
                        .header("X-Client-Id", "11111111-1111-1111-1111-111111111111")
                        .header("Idempotency-Key", "idem-rev-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reversePayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reversesJournalEntryId").value(originalId.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString());

        UUID reversalId = UUID.fromString(reversal.get("id").asText());

        mockMvc.perform(get("/journal-entries/{id}/postings", reversalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postings").isArray())
                .andExpect(jsonPath("$.postings[0].currency").value("EUR"));

        // Second reversal should fail due to unique reversal constraint.
        mockMvc.perform(post("/journal-entries/{id}/reversal", originalId)
                        .header("X-Client-Id", "11111111-1111-1111-1111-111111111111")
                        .header("Idempotency-Key", "idem-rev-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void integrityEndpoint_detectsTampering() throws Exception {
        UUID customer = createAccount("Alice", "CUSTOMER");
        UUID internal = createAccount("Ops", "INTERNAL");

                fund(customer, internal, "fund-3", "10.0000");

        String payload = "{" +
                "\"description\":\"payment\"," +
                "\"postings\":[" +
                "{\"accountId\":\"" + customer + "\",\"currency\":\"EUR\",\"amount\":\"-10.0000\"}," +
                "{\"accountId\":\"" + internal + "\",\"currency\":\"EUR\",\"amount\":\"10.0000\"}" +
                "]}";

        JsonNode original = createJournalEntry("11111111-1111-1111-1111-111111111111", "idem-3", payload);
        UUID originalId = UUID.fromString(original.get("id").asText());

        mockMvc.perform(get("/ledger/integrity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        // Tamper with stored entry_hash (requires disabling append-only trigger).
        jdbc.execute("ALTER TABLE journal_entries DISABLE TRIGGER journal_entries_forbid_ud");
        jdbc.update("UPDATE journal_entries SET entry_hash = repeat('f', 64) WHERE id = ?", originalId);
        jdbc.execute("ALTER TABLE journal_entries ENABLE TRIGGER journal_entries_forbid_ud");

        mockMvc.perform(get("/ledger/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.issues").isArray());
    }

    private UUID createAccount(String name, String accountType) throws Exception {
        String payload = "{\"name\":\"" + name + "\",\"accountType\":\"" + accountType + "\"}";

        String response = mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return UUID.fromString(json.get("id").asText());
    }

    private JsonNode createJournalEntry(String clientId, String idempotencyKey, String payload) throws Exception {
        String response = mockMvc.perform(post("/journal-entries")
                        .header("X-Client-Id", clientId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response);
    }

    private void fund(UUID customerAccountId, UUID internalAccountId, String idempotencyKey, String amount) throws Exception {
        String payload = "{" +
                "\"description\":\"funding\"," +
                "\"postings\":[" +
                "{\"accountId\":\"" + internalAccountId + "\",\"currency\":\"EUR\",\"amount\":\"-" + amount + "\"}," +
                "{\"accountId\":\"" + customerAccountId + "\",\"currency\":\"EUR\",\"amount\":\"" + amount + "\"}" +
                "]}";

        createJournalEntry("11111111-1111-1111-1111-111111111111", idempotencyKey, payload);
    }
}
