package com.aleksastajic.ledger.accounts;

import com.aleksastajic.ledger.balances.AccountBalanceService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AccountController.class)
@Import(ProblemDetailsAdvice.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

        @MockBean
        private AccountBalanceService accountBalanceService;

    @Test
    void create_returns201AndBody() throws Exception {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(accountService.create(any(), any()))
                .thenReturn(new AccountEntity(id, "Alice", AccountType.CUSTOMER, Instant.parse("2026-01-14T00:00:00Z")));

        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice\",\"accountType\":\"CUSTOMER\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/accounts/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.accountType").value("CUSTOMER"));
    }

    @Test
    void create_withBlankName_returns400Problem() throws Exception {
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"accountType\":\"CUSTOMER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:problem:validation"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void get_returns200AndBody() throws Exception {
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(accountService.getById(id))
                .thenReturn(new AccountEntity(id, "Ops", AccountType.INTERNAL, Instant.parse("2026-01-14T00:00:00Z")));

        mockMvc.perform(get("/accounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Ops"))
                .andExpect(jsonPath("$.accountType").value("INTERNAL"));
    }

        @Test
        void balances_returns200AndBody() throws Exception {
                UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
                org.mockito.Mockito.when(accountBalanceService.getBalances(id))
                                .thenReturn(List.of(new AccountBalanceService.AccountBalanceResponse("EUR", "12.3400")));

                mockMvc.perform(get("/accounts/{id}/balances", id))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.balances").isArray())
                                .andExpect(jsonPath("$.balances[0].currency").value("EUR"))
                                .andExpect(jsonPath("$.balances[0].balance").value("12.3400"));
        }
}
