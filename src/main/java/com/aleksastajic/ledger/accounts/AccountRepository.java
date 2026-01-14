package com.aleksastajic.ledger.accounts;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<AccountEntity> findWithLockById(UUID id);
}
