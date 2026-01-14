package com.aleksastajic.ledger.ledger.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRequestRepository extends JpaRepository<IdempotencyRequestEntity, UUID> {
    Optional<IdempotencyRequestEntity> findByClientIdAndIdempotencyKey(UUID clientId, String idempotencyKey);

    @Modifying
    @Transactional
    @Query(
        value = "INSERT INTO idempotency_requests (id, client_id, idempotency_key, request_hash, hash_algo, journal_entry_id, created_at) " +
            "VALUES (:id, :clientId, :idempotencyKey, :requestHash, :hashAlgo, NULL, :createdAt) " +
            "ON CONFLICT (client_id, idempotency_key) DO NOTHING",
        nativeQuery = true
    )
    int insertIfAbsent(
        @Param("id") UUID id,
        @Param("clientId") UUID clientId,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("requestHash") String requestHash,
        @Param("hashAlgo") String hashAlgo,
        @Param("createdAt") Instant createdAt
    );
}
