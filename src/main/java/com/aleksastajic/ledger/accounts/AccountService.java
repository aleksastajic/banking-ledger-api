package com.aleksastajic.ledger.accounts;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final Clock clock;

    @Autowired
    public AccountService(AccountRepository accountRepository) {
        this(accountRepository, Clock.systemUTC());
    }

    AccountService(AccountRepository accountRepository, Clock clock) {
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    public AccountEntity create(String name, AccountType accountType) {
        Instant now = Instant.now(clock);
        AccountEntity entity = new AccountEntity(UUID.randomUUID(), name, accountType, now);
        return accountRepository.save(entity);
    }

    public AccountEntity getById(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Account not found"));
    }
}
