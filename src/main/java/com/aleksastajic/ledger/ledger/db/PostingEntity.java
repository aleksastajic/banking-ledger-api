package com.aleksastajic.ledger.ledger.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "postings")
public class PostingEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntryEntity journalEntry;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "memo")
    private String memo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PostingEntity() {
    }

    public PostingEntity(UUID id, JournalEntryEntity journalEntry, UUID accountId, String currency, BigDecimal amount, String memo, Instant createdAt) {
        this.id = id;
        this.journalEntry = journalEntry;
        this.accountId = accountId;
        this.currency = currency;
        this.amount = amount;
        this.memo = memo;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getMemo() {
        return memo;
    }
}
