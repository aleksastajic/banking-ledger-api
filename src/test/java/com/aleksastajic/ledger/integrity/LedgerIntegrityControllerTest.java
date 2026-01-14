package com.aleksastajic.ledger.integrity;

import com.aleksastajic.ledger.config.ProblemDetailsAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = LedgerIntegrityController.class)
@Import(ProblemDetailsAdvice.class)
class LedgerIntegrityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LedgerIntegrityService ledgerIntegrityService;

    @Test
    void verify_returns200AndReport() throws Exception {
        UUID lastEntryId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(ledgerIntegrityService.verify()).thenReturn(new LedgerIntegrityReport(
                "OK",
                2L,
                2L,
                "abcd",
                lastEntryId,
                Instant.parse("2026-01-14T00:00:00Z"),
                List.of()
        ));

        mockMvc.perform(get("/ledger/integrity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.entriesChecked").value(2))
                .andExpect(jsonPath("$.headSeqNo").value(2))
                .andExpect(jsonPath("$.lastEntryId").value(lastEntryId.toString()))
                .andExpect(jsonPath("$.issues").isArray());
    }

    @Test
    void verify_returns409OnFailure() throws Exception {
    UUID entryId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    when(ledgerIntegrityService.verify()).thenReturn(new LedgerIntegrityReport(
        "FAILED",
        3L,
        3L,
        "abcd",
        entryId,
        Instant.parse("2026-01-14T00:00:00Z"),
        List.of(new LedgerIntegrityReport.Issue(
            "ENTRY_HASH_MISMATCH",
            3L,
            entryId,
            "entryHash does not match recomputed hash",
            "expected",
            "actual"
        ))
    ));

    mockMvc.perform(get("/ledger/integrity"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value("FAILED"))
        .andExpect(jsonPath("$.issues").isArray())
        .andExpect(jsonPath("$.issues[0].code").value("ENTRY_HASH_MISMATCH"));
    }
}
