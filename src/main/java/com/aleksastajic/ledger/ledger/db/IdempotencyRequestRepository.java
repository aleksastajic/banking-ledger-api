package com.aleksastajic.ledger.ledger.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRequestRepository extends JpaRepository<IdempotencyRequestEntity, UUID> {
    Optional<IdempotencyRequestEntity> findByClientIdAndIdempotencyKey(UUID clientId, String idempotencyKey);
}
