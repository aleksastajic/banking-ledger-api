package com.aleksastajic.ledger.ledger.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PostingRepository extends JpaRepository<PostingEntity, UUID> {

	List<PostingEntity> findByJournalEntry_IdOrderById(UUID journalEntryId);

		@Query(value = """
						select
							a.id as accountId,
							a.account_type as accountType,
							p.currency as currency,
							sum(p.amount) as balance
						from accounts a
						join postings p on p.account_id = a.id
						where a.id in (:accountIds)
						group by a.id, a.account_type, p.currency
						""", nativeQuery = true)
		List<AccountBalanceRow> aggregateBalances(@Param("accountIds") List<UUID> accountIds);

		interface AccountBalanceRow {
				UUID getAccountId();

				String getAccountType();

				String getCurrency();

				BigDecimal getBalance();
		}
}
