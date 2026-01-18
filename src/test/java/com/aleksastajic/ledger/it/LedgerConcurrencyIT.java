package com.aleksastajic.ledger.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
class LedgerConcurrencyIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void concurrentCreate_sameIdempotencyKey_isEventuallyConsistent_andSingleRowInDb() throws Exception {
        UUID customer = createAccount("Alice", "CUSTOMER");
        UUID internal = createAccount("Ops", "INTERNAL");
        fund(customer, internal, "fund-conc-1", "10.0000");

        UUID clientId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String idempotencyKey = "idem-concurrent";

        String payload = "{" +
                "\"description\":\"payment-concurrent\"," +
                "\"postings\":[" +
                "{\"accountId\":\"" + customer + "\",\"currency\":\"EUR\",\"amount\":\"-10.0000\"}," +
                "{\"accountId\":\"" + internal + "\",\"currency\":\"EUR\",\"amount\":\"10.0000\"}" +
                "]}";

        int calls = 24;

        ExecutorService pool = Executors.newFixedThreadPool(calls);
        try {
            CountDownLatch ready = new CountDownLatch(calls);
            CountDownLatch start = new CountDownLatch(1);

            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < calls; i++) {
                tasks.add(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return postJournalEntryUntilCreated(clientId, idempotencyKey, payload, Duration.ofSeconds(3));
                });
            }

            List<Future<String>> futures = tasks.stream().map(pool::submit).toList();

            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Set<String> ids = new HashSet<>();
            for (Future<String> f : futures) {
                ids.add(f.get(20, TimeUnit.SECONDS));
            }

            assertThat(ids).hasSize(1);
            assertThat(jdbc.queryForObject(
                    "select count(*) from journal_entries where description = 'payment-concurrent'",
                    Integer.class
            )).isEqualTo(1);

            assertThat(jdbc.queryForObject(
                    "select count(*) from idempotency_requests where idempotency_key = '" + idempotencyKey + "'",
                    Integer.class
            )).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentCreate_distinctIdempotencyKeys_keepsSeqNoContiguous_andUpdatesChainHead() throws Exception {
        UUID customer = createAccount("Alice", "CUSTOMER");
        UUID internal = createAccount("Ops", "INTERNAL");
        fund(customer, internal, "fund-conc-2", "50.0000");

        UUID clientId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        int calls = 25;

        ExecutorService pool = Executors.newFixedThreadPool(calls);
        try {
            CountDownLatch ready = new CountDownLatch(calls);
            CountDownLatch start = new CountDownLatch(1);

            List<Callable<UUID>> tasks = new ArrayList<>();
            for (int i = 0; i < calls; i++) {
                String idempotencyKey = "idem-batch-" + i;
                String payload = "{" +
                        "\"description\":\"bulk-" + i + "\"," +
                        "\"postings\":[" +
                        "{\"accountId\":\"" + customer + "\",\"currency\":\"EUR\",\"amount\":\"-1.0000\"}," +
                        "{\"accountId\":\"" + internal + "\",\"currency\":\"EUR\",\"amount\":\"1.0000\"}" +
                        "]}";

                tasks.add(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    String id = postJournalEntryUntilCreated(clientId, idempotencyKey, payload, Duration.ofSeconds(3));
                    return UUID.fromString(id);
                });
            }

            List<Future<UUID>> futures = tasks.stream().map(pool::submit).toList();

            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Set<UUID> ids = new HashSet<>();
            for (Future<UUID> f : futures) {
                ids.add(f.get(30, TimeUnit.SECONDS));
            }

            assertThat(ids).hasSize(calls);

            Integer entryCount = jdbc.queryForObject("select count(*) from journal_entries", Integer.class);
            assertThat(entryCount).isEqualTo(1 + calls); // funding + bulk

            // Verify seq_no is contiguous (1..N)
            Integer gaps = jdbc.queryForObject(
                    "select count(*) from (" +
                            "  select seq_no, lag(seq_no) over (order by seq_no) as prev" +
                            "  from journal_entries" +
                            ") t where prev is not null and seq_no <> prev + 1",
                    Integer.class
            );
            assertThat(gaps).isEqualTo(0);

            Long maxSeq = jdbc.queryForObject("select max(seq_no) from journal_entries", Long.class);
            Long headSeq = jdbc.queryForObject(
                    "select last_seq_no from ledger_chain_head where id = '00000000-0000-0000-0000-000000000001'",
                    Long.class
            );
            assertThat(headSeq).isEqualTo(maxSeq);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentReversal_allowsOnlyOneReversalEntry() throws Exception {
        UUID customer = createAccount("Alice", "CUSTOMER");
        UUID internal = createAccount("Ops", "INTERNAL");
        fund(customer, internal, "fund-conc-3", "10.0000");

        JsonNode original = createJournalEntry(
                "11111111-1111-1111-1111-111111111111",
                "idem-orig",
                "{" +
                        "\"description\":\"orig\"," +
                        "\"postings\":[" +
                        "{\"accountId\":\"" + customer + "\",\"currency\":\"EUR\",\"amount\":\"-10.0000\"}," +
                        "{\"accountId\":\"" + internal + "\",\"currency\":\"EUR\",\"amount\":\"10.0000\"}" +
                        "]}"
        );
        UUID originalId = UUID.fromString(original.get("id").asText());

        int calls = 16;

        ExecutorService pool = Executors.newFixedThreadPool(calls);
        try {
            CountDownLatch ready = new CountDownLatch(calls);
            CountDownLatch start = new CountDownLatch(1);

            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < calls; i++) {
                String idempotencyKey = "idem-rev-conc-" + i;
                tasks.add(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);

                    MvcResult r = mockMvc.perform(post("/journal-entries/{id}/reversal", originalId)
                                    .header("X-Client-Id", "11111111-1111-1111-1111-111111111111")
                                    .header("Idempotency-Key", idempotencyKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}"))
                            .andReturn();

                    int status = r.getResponse().getStatus();
                    if (status == 201 || status == 409) {
                        return status;
                    }
                    String body = r.getResponse().getContentAsString();
                    fail("Unexpected status " + status + " body=" + body);
                    return status;
                });
            }

            List<Future<Integer>> futures = tasks.stream().map(pool::submit).toList();

            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int created = 0;
            int conflicts = 0;
            for (Future<Integer> f : futures) {
                int status = f.get(30, TimeUnit.SECONDS);
                if (status == 201) created++;
                if (status == 409) conflicts++;
            }

            assertThat(created).isEqualTo(1);
            assertThat(conflicts).isEqualTo(calls - 1);

            Integer reversals = jdbc.queryForObject(
                    "select count(*) from journal_entries where reverses_journal_entry_id = ?",
                    Integer.class,
                    originalId
            );
            assertThat(reversals).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private String postJournalEntryUntilCreated(UUID clientId, String idempotencyKey, String payload, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            MvcResult r = mockMvc.perform(post("/journal-entries")
                            .header("X-Client-Id", clientId.toString())
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andReturn();

            int status = r.getResponse().getStatus();
            String body = r.getResponse().getContentAsString();

            if (status == 201) {
                JsonNode json = objectMapper.readTree(body);
                return json.get("id").asText();
            }

            if (status == 409 && body.contains("Idempotent request is still in progress")) {
                Thread.sleep(25);
                continue;
            }

            fail("Unexpected response: status=" + status + " body=" + body);
        }

        fail("Timed out waiting for idempotent create to complete");
        return null;
    }

    private UUID createAccount(String name, String accountType) throws Exception {
        String payload = "{\"name\":\"" + name + "\",\"accountType\":\"" + accountType + "\"}";

        String response = mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
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
