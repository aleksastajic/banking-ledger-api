package com.aleksastajic.ledger.ledger.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface LedgerChainHeadRepository extends JpaRepository<LedgerChainHeadEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from LedgerChainHeadEntity h where h.id = :id")
    Optional<LedgerChainHeadEntity> findByIdForUpdate(@Param("id") UUID id);
}
