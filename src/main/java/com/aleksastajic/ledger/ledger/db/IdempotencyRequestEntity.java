package com.aleksastajic.ledger.ledger.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_requests")
public class IdempotencyRequestEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String requestHash;

    @Column(name = "hash_algo", nullable = false)
    private String hashAlgo;

    @ManyToOne
    @JoinColumn(name = "journal_entry_id")
    private JournalEntryEntity journalEntry;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRequestEntity() {
    }

    public IdempotencyRequestEntity(UUID id, UUID clientId, String idempotencyKey, String requestHash, String hashAlgo, JournalEntryEntity journalEntry, Instant createdAt) {
        this.id = id;
        this.clientId = clientId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.hashAlgo = hashAlgo;
        this.journalEntry = journalEntry;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public JournalEntryEntity getJournalEntry() {
        return journalEntry;
    }

    public void setJournalEntry(JournalEntryEntity journalEntry) {
        this.journalEntry = journalEntry;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
