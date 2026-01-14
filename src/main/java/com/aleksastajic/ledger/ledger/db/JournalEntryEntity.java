package com.aleksastajic.ledger.ledger.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "journal_entries")
public class JournalEntryEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "seq_no", nullable = false)
    private long seqNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "description")
    private String description;

    @Column(name = "reverses_journal_entry_id")
    private UUID reversesJournalEntryId;

    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;

    @Column(name = "entry_hash", nullable = false, length = 64)
    private String entryHash;

    @Column(name = "hash_algo", nullable = false)
    private String hashAlgo;

    protected JournalEntryEntity() {
    }

    public JournalEntryEntity(UUID id, long seqNo, Instant createdAt, String description, UUID reversesJournalEntryId, String prevHash, String entryHash, String hashAlgo) {
        this.id = id;
        this.seqNo = seqNo;
        this.createdAt = createdAt;
        this.description = description;
        this.reversesJournalEntryId = reversesJournalEntryId;
        this.prevHash = prevHash;
        this.entryHash = entryHash;
        this.hashAlgo = hashAlgo;
    }

    public UUID getId() {
        return id;
    }

    public long getSeqNo() {
        return seqNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getDescription() {
        return description;
    }

    public UUID getReversesJournalEntryId() {
        return reversesJournalEntryId;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public String getEntryHash() {
        return entryHash;
    }

    public String getHashAlgo() {
        return hashAlgo;
    }
}
