package com.aleksastajic.ledger.journal;

import com.aleksastajic.ledger.config.ProblemDetailsAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = JournalEntryController.class)
@Import(ProblemDetailsAdvice.class)
class JournalEntryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JournalEntryService journalEntryService;

    @Test
    void create_returns201AndBody() throws Exception {
        UUID entryId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(journalEntryService.create(any(), anyString(), any()))
                .thenReturn(new JournalEntryResponse(
                        entryId,
                        1L,
                        Instant.parse("2026-01-14T00:00:00Z"),
                        null,
                        "0000",
                        "abcd"
                ));

        mockMvc.perform(post("/journal-entries")
                        .header("X-Client-Id", "11111111-1111-1111-1111-111111111111")
                        .header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"description\":\"test\"," +
                                "\"postings\":[" +
                                "{\"accountId\":\"22222222-2222-2222-2222-222222222222\",\"currency\":\"EUR\",\"amount\":\"10.00\"}," +
                                "{\"accountId\":\"44444444-4444-4444-4444-444444444444\",\"currency\":\"EUR\",\"amount\":\"-10.00\"}" +
                                "]" +
                                "}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/journal-entries/" + entryId))
                .andExpect(jsonPath("$.id").value(entryId.toString()))
                .andExpect(jsonPath("$.seqNo").value(1))
                .andExpect(jsonPath("$.prevHash").value("0000"))
                .andExpect(jsonPath("$.entryHash").value("abcd"));
    }

    @Test
    void create_missingHeaders_returns400Problem() throws Exception {
        mockMvc.perform(post("/journal-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postings\":[]}"))
                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.type").value("urn:problem:bad-request"));
    }

    @Test
    void create_invalidPayload_returns400Problem() throws Exception {
        mockMvc.perform(post("/journal-entries")
                        .header("X-Client-Id", "11111111-1111-1111-1111-111111111111")
                        .header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"postings\":[" +
                                "{\"accountId\":null,\"currency\":\"EU\",\"amount\":\"\"}" +
                                "]" +
                                "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:problem:validation"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void getById_returns200AndBody() throws Exception {
        UUID entryId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(journalEntryService.getById(entryId))
                .thenReturn(new JournalEntryResponse(
                        entryId,
                        42L,
                        Instant.parse("2026-01-14T00:00:00Z"),
                        null,
                        "prev",
                        "hash"
                ));

        mockMvc.perform(get("/journal-entries/{id}", entryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(entryId.toString()))
                .andExpect(jsonPath("$.seqNo").value(42))
                .andExpect(jsonPath("$.prevHash").value("prev"))
                .andExpect(jsonPath("$.entryHash").value("hash"));
    }

    @Test
    void listPostings_returns200AndBody() throws Exception {
        UUID entryId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(journalEntryService.listPostings(eq(entryId)))
                .thenReturn(List.of(
                        new PostingResponse(
                                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                                "EUR",
                                "10.0000",
                                null
                        )
                ));

        mockMvc.perform(get("/journal-entries/{id}/postings", entryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postings").isArray())
                .andExpect(jsonPath("$.postings[0].currency").value("EUR"))
                .andExpect(jsonPath("$.postings[0].amount").value("10.0000"));
    }

    @Test
    void reverse_returns201AndBody() throws Exception {
        UUID originalId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID reversalId = UUID.fromString("55555555-5555-5555-5555-555555555555");

        when(journalEntryService.reverse(eq(originalId), any(), anyString(), any()))
                .thenReturn(new JournalEntryResponse(
                        reversalId,
                        2L,
                        Instant.parse("2026-01-14T00:00:00Z"),
                        originalId,
                        "prev",
                        "hash"
                ));

        mockMvc.perform(post("/journal-entries/{id}/reversal", originalId)
                        .header("X-Client-Id", "11111111-1111-1111-1111-111111111111")
                        .header("Idempotency-Key", "k-rev")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"customer refund\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/journal-entries/" + reversalId))
                .andExpect(jsonPath("$.id").value(reversalId.toString()))
                .andExpect(jsonPath("$.seqNo").value(2));
    }

    @Test
    void reverse_missingHeaders_returns400Problem() throws Exception {
        UUID originalId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        mockMvc.perform(post("/journal-entries/{id}/reversal", originalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:problem:bad-request"));
    }
}
