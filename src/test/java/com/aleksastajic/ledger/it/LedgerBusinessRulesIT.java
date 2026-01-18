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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LedgerBusinessRulesIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createEntry_rejectsUnbalancedPostingsPerCurrency() throws Exception {
        UUID customer = createAccount("Alice", "CUSTOMER");
        UUID internal = createAccount("Ops", "INTERNAL");

        // EUR does not net to 0 (sum is +1.0000)
        String payload = "{" +
                "\"description\":\"bad-unbalanced\"," +
                "\"postings\":[" +
                "{\"accountId\":\"" + customer + "\",\"currency\":\"EUR\",\"amount\":\"-9.0000\"}," +
                "{\"accountId\":\"" + internal + "\",\"currency\":\"EUR\",\"amount\":\"10.0000\"}" +
                "]}";

        mockMvc.perform(post("/journal-entries")
                        .header("X-Client-Id", "11111111-1111-1111-1111-111111111111")
                        .header("Idempotency-Key", "unbalanced-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select count(*) from journal_entries", Integer.class)).isEqualTo(0);
    }

    @Test
    void createEntry_allowsMultiCurrencyWhenEachCurrencyBalances() throws Exception {
        UUID customer = createAccount("Alice", "CUSTOMER");
        UUID internal = createAccount("Ops", "INTERNAL");

        // Fund customer with both EUR and USD.
        fund(customer, internal, "fund-eur", "EUR", "10.0000");
        fund(customer, internal, "fund-usd", "USD", "20.0000");

        String payload = "{" +
                "\"description\":\"multi-currency\"," +
                "\"postings\":[" +
                // EUR leg
                "{\"accountId\":\"" + customer + "\",\"currency\":\"EUR\",\"amount\":\"-10.0000\"}," +
                "{\"accountId\":\"" + internal + "\",\"currency\":\"EUR\",\"amount\":\"10.0000\"}," +
                // USD leg
                "{\"accountId\":\"" + customer + "\",\"currency\":\"USD\",\"amount\":\"-20.0000\"}," +
                "{\"accountId\":\"" + internal + "\",\"currency\":\"USD\",\"amount\":\"20.0000\"}" +
                "]}";

        mockMvc.perform(post("/journal-entries")
                        .header("X-Client-Id", "11111111-1111-1111-1111-111111111111")
                        .header("Idempotency-Key", "multi-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject(
                "select count(*) from journal_entries where description = 'multi-currency'",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void createEntry_rejectsCustomerGoingNegative() throws Exception {
        UUID customer = createAccount("Alice", "CUSTOMER");
        UUID internal = createAccount("Ops", "INTERNAL");

        fund(customer, internal, "fund-1", "EUR", "5.0000");

        String payload = "{" +
                "\"description\":\"overspend\"," +
                "\"postings\":[" +
                "{\"accountId\":\"" + customer + "\",\"currency\":\"EUR\",\"amount\":\"-6.0000\"}," +
                "{\"accountId\":\"" + internal + "\",\"currency\":\"EUR\",\"amount\":\"6.0000\"}" +
                "]}";

        mockMvc.perform(post("/journal-entries")
                        .header("X-Client-Id", "11111111-1111-1111-1111-111111111111")
                        .header("Idempotency-Key", "neg-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());

        // Ensure the failed transaction did not write the entry.
        assertThat(jdbc.queryForObject(
                "select count(*) from journal_entries where description = 'overspend'",
                Integer.class
        )).isEqualTo(0);
    }

    @Test
    void dbTriggers_forbidUpdateAndDelete_onAppendOnlyTables() throws Exception {
        UUID customer = createAccount("Alice", "CUSTOMER");
        UUID internal = createAccount("Ops", "INTERNAL");
        fund(customer, internal, "fund-appendonly", "EUR", "10.0000");

        String payload = "{" +
                "\"description\":\"append-only\"," +
                "\"postings\":[" +
                "{\"accountId\":\"" + customer + "\",\"currency\":\"EUR\",\"amount\":\"-1.0000\"}," +
                "{\"accountId\":\"" + internal + "\",\"currency\":\"EUR\",\"amount\":\"1.0000\"}" +
                "]}";

        JsonNode entry = createJournalEntry("11111111-1111-1111-1111-111111111111", "appendonly-1", payload);
        UUID journalEntryId = UUID.fromString(entry.get("id").asText());

        UUID postingId = jdbc.queryForObject(
                "select id from postings where journal_entry_id = ? order by created_at asc limit 1",
                UUID.class,
                journalEntryId
        );

        assertThatThrownBy(() -> jdbc.update(
                "update journal_entries set description = 'tampered' where id = ?",
                journalEntryId
        )).isNotNull();

        assertThatThrownBy(() -> jdbc.update(
                "delete from journal_entries where id = ?",
                journalEntryId
        )).isNotNull();

        assertThatThrownBy(() -> jdbc.update(
                "update postings set amount = amount where id = ?",
                postingId
        )).isNotNull();

        assertThatThrownBy(() -> jdbc.update(
                "delete from postings where id = ?",
                postingId
        )).isNotNull();
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

    private void fund(UUID customerAccountId, UUID internalAccountId, String idempotencyKey, String currency, String amount) throws Exception {
        String payload = "{" +
                "\"description\":\"funding\"," +
                "\"postings\":[" +
                "{\"accountId\":\"" + internalAccountId + "\",\"currency\":\"" + currency + "\",\"amount\":\"-" + amount + "\"}," +
                "{\"accountId\":\"" + customerAccountId + "\",\"currency\":\"" + currency + "\",\"amount\":\"" + amount + "\"}" +
                "]}";

        createJournalEntry("11111111-1111-1111-1111-111111111111", idempotencyKey, payload);
    }
}
