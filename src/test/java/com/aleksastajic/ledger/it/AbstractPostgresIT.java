package com.aleksastajic.ledger.it;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SuppressWarnings("resource")
public abstract class AbstractPostgresIT {

    private static final boolean USE_TESTCONTAINERS = Boolean.getBoolean("it.useTestcontainers");
    private static final PostgreSQLContainer<?> POSTGRES_CONTAINER;

    static {
        if (USE_TESTCONTAINERS) {
            POSTGRES_CONTAINER = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.11"))
                    .withDatabaseName("ledger")
                    .withUsername("postgres")
                    .withPassword("postgres");
            POSTGRES_CONTAINER.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    POSTGRES_CONTAINER.stop();
                } catch (Exception ignored) {
                }
            }));
        } else {
            POSTGRES_CONTAINER = null;
        }
    }

    @DynamicPropertySource
    static void overrideDatasourceIfUsingTestcontainers(DynamicPropertyRegistry registry) {
        if (!USE_TESTCONTAINERS) {
            return;
        }

        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        // TRUNCATE does not fire our UPDATE/DELETE forbid triggers.
        jdbc.execute("TRUNCATE TABLE postings, idempotency_requests, journal_entries, accounts RESTART IDENTITY CASCADE");
        jdbc.update(
                "UPDATE ledger_chain_head SET last_seq_no = 0, last_hash = repeat('0', 64), updated_at = now() WHERE id = '00000000-0000-0000-0000-000000000001'"
        );
    }
}
