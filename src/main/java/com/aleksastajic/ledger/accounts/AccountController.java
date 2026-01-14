package com.aleksastajic.ledger.accounts;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.aleksastajic.ledger.balances.AccountBalanceService;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService accountService;
    private final AccountBalanceService accountBalanceService;

    public AccountController(AccountService accountService, AccountBalanceService accountBalanceService) {
        this.accountService = accountService;
        this.accountBalanceService = accountBalanceService;
    }

    @PostMapping
    @Operation(summary = "Create account")
    public ResponseEntity<AccountResponse> create(
            @Valid @RequestBody CreateAccountRequest request,
            UriComponentsBuilder uriBuilder
    ) {
        AccountEntity created = accountService.create(request.name(), request.accountType());

        URI location = uriBuilder.path("/accounts/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location)
                .body(AccountResponse.from(created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account by id")
    public AccountResponse getById(@PathVariable UUID id) {
        return AccountResponse.from(accountService.getById(id));
    }

    @GetMapping("/{id}/balances")
    @Operation(summary = "Get account balances")
    public BalancesResponse getBalances(@PathVariable UUID id) {
        return new BalancesResponse(accountBalanceService.getBalances(id));
    }

    public record BalancesResponse(List<AccountBalanceService.AccountBalanceResponse> balances) {
    }

    public record CreateAccountRequest(
            @NotBlank String name,
            @NotNull AccountType accountType
    ) {
    }

    public record AccountResponse(
            UUID id,
            String name,
            AccountType accountType
    ) {
        static AccountResponse from(AccountEntity entity) {
            return new AccountResponse(entity.getId(), entity.getName(), entity.getAccountType());
        }
    }
}
