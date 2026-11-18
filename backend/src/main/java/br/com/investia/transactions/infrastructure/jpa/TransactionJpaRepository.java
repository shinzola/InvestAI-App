package br.com.investia.transactions.infrastructure.jpa;

import br.com.investia.transactions.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TransactionJpaRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findAllByUserIdOrderByDateDesc(UUID userId, Pageable pageable);

    List<Transaction> findAllByUserIdAndDateBetweenOrderByDateAsc(UUID userId, LocalDate start, LocalDate end);

    /** Total por categoria no período (p/ orçamento e analytics). */
    @Query(value = """
            SELECT t.category_id AS "categoryId", COALESCE(SUM(t.amount), 0) AS "total"
            FROM transactions t
            WHERE t.user_id = :userId AND t.type = 'EXPENSE' AND t.status = 'CLEARED'
              AND t.date BETWEEN :start AND :end
            GROUP BY t.category_id
            """, nativeQuery = true)
    List<CategoryTotal> sumExpensesByCategory(@Param("userId") UUID userId,
                                              @Param("start") LocalDate start,
                                              @Param("end") LocalDate end);

    interface CategoryTotal {
        UUID getCategoryId();

        BigDecimal getTotal();
    }

    @Query(value = """
            SELECT COALESCE(SUM(t.amount), 0) FROM transactions t
            WHERE t.user_id = :userId AND t.type = 'INCOME'
              AND t.date BETWEEN :start AND :end
            """, nativeQuery = true)
    BigDecimal sumIncome(@Param("userId") UUID userId,
                         @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query(value = """
            SELECT COALESCE(SUM(t.amount), 0) FROM transactions t
            WHERE t.user_id = :userId AND t.type = 'EXPENSE'
              AND t.date BETWEEN :start AND :end
            """, nativeQuery = true)
    BigDecimal sumExpense(@Param("userId") UUID userId,
                          @Param("start") LocalDate start, @Param("end") LocalDate end);

    /** Média mensal de despesas dos últimos n meses (reserva de emergência, alertas). */
    @Query(value = """
            SELECT COALESCE(AVG(m.total), 0) FROM (
                SELECT DATE_TRUNC('month', t.date) AS m, SUM(t.amount) AS total
                FROM transactions t
                WHERE t.user_id = :userId AND t.type = 'EXPENSE' AND t.status = 'CLEARED'
                  AND t.date >= :start
                GROUP BY 1
            ) m
            """, nativeQuery = true)
    BigDecimal avgMonthlyExpense(@Param("userId") UUID userId, @Param("start") LocalDate start);

    /** Despesas essenciais dos últimos 3 meses (reserva de emergência = 6x média). */
    @Query(value = """
            SELECT COALESCE(AVG(m.total), 0) FROM (
                SELECT DATE_TRUNC('month', t.date) AS m, SUM(t.amount) AS total
                FROM transactions t JOIN categories c ON c.id = t.category_id
                WHERE t.user_id = :userId AND t.type = 'EXPENSE' AND t.status = 'CLEARED'
                  AND t.date >= :start AND c.name IN ('Moradia', 'Alimentação', 'Saúde', 'Transporte')
                GROUP BY 1
            ) m
            """, nativeQuery = true)
    BigDecimal avgMonthlyEssentialExpense(@Param("userId") UUID userId, @Param("start") LocalDate start);
}
