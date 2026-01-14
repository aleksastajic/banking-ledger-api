package com.aleksastajic.ledger.ledger.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_chain_head")
public class LedgerChainHeadEntity {

    public static final UUID SINGLETON_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "last_seq_no", nullable = false)
    private long lastSeqNo;

    @Column(name = "last_hash", nullable = false, length = 64)
    private String lastHash;

    @Column(name = "hash_algo", nullable = false)
    private String hashAlgo;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerChainHeadEntity() {
    }

    public UUID getId() {
        return id;
    }

    public long getLastSeqNo() {
        return lastSeqNo;
    }

    public void setLastSeqNo(long lastSeqNo) {
        this.lastSeqNo = lastSeqNo;
    }

    public String getLastHash() {
        return lastHash;
    }

    public void setLastHash(String lastHash) {
        this.lastHash = lastHash;
    }

    public String getHashAlgo() {
        return hashAlgo;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
