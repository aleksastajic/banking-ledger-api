package com.aleksastajic.ledger.balances;

import com.aleksastajic.ledger.accounts.AccountRepository;
import com.aleksastajic.ledger.ledger.db.PostingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class AccountBalanceService {

    private final AccountRepository accountRepository;
    private final PostingRepository postingRepository;

    public AccountBalanceService(AccountRepository accountRepository, PostingRepository postingRepository) {
        this.accountRepository = accountRepository;
        this.postingRepository = postingRepository;
    }

    public List<AccountBalanceResponse> getBalances(UUID accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }

        return postingRepository.aggregateBalances(List.of(accountId)).stream()
                .map(r -> new AccountBalanceResponse(r.getCurrency(), r.getBalance().toPlainString()))
                .toList();
    }

    public record AccountBalanceResponse(
            String currency,
            String balance
    ) {
    }
}
