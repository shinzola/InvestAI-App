package br.com.investia.accounts.infrastructure.jpa;

import br.com.investia.accounts.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface AccountJpaRepository extends JpaRepository<Account, UUID> {

    List<Account> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    /** Saldo consolidado: inicial + receitas - despesas + transferências líquidas. */
    @Query(value = """
            SELECT COALESCE(SUM(CASE
                WHEN t.type = 'INCOME' THEN t.amount
                WHEN t.type = 'EXPENSE' THEN -t.amount
                WHEN t.type = 'TRANSFER' AND t.destination_account_id = a.id THEN t.amount
                WHEN t.type = 'TRANSFER' AND t.account_id = a.id THEN -t.amount
                ELSE 0 END), 0)
            FROM accounts a
            LEFT JOIN transactions t
              ON t.account_id = a.id OR t.destination_account_id = a.id
            WHERE a.user_id = :userId AND a.id = :accountId
            """, nativeQuery = true)
    BigDecimal computedBalance(@Param("userId") UUID userId, @Param("accountId") UUID accountId);
}
