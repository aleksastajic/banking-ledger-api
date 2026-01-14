package com.aleksastajic.ledger.ledger.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, UUID> {

	List<JournalEntryEntity> findAllByOrderBySeqNoAsc();

	boolean existsByReversesJournalEntryId(UUID reversesJournalEntryId);
}
