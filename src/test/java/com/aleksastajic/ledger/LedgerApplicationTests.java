package com.aleksastajic.ledger;

import com.aleksastajic.ledger.accounts.AccountRepository;
import com.aleksastajic.ledger.balances.AccountBalanceService;
import com.aleksastajic.ledger.journal.JournalEntryService;
import com.aleksastajic.ledger.ledger.LedgerWriter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
class LedgerApplicationTests {

    @MockBean
    private AccountRepository accountRepository;

    @MockBean
    private LedgerWriter ledgerWriter;

    @MockBean
    private JournalEntryService journalEntryService;

    @MockBean
    private AccountBalanceService accountBalanceService;

    @Test
    void contextLoads() {
    }
}
