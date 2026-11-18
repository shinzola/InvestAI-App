package br.com.investia.earnings.infrastructure.jpa;

import br.com.investia.earnings.domain.Dividend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DividendJpaRepository extends JpaRepository<Dividend, UUID> {

    List<Dividend> findAllByUserIdOrderByDateDesc(UUID userId);

    List<Dividend> findAllByUserIdAndDateBetweenOrderByDateAsc(UUID userId, LocalDate start, LocalDate end);

    @Query(value = """
            SELECT DATE_TRUNC('month', d.date) AS m, COALESCE(SUM(d.amount - d.withholding_tax), 0) AS total
            FROM dividends d
            WHERE d.user_id = :userId AND d.date >= :start
            GROUP BY 1 ORDER BY 1
            """, nativeQuery = true)
    List<MonthlyTotal> monthlyTotals(@Param("userId") UUID userId, @Param("start") LocalDate start);

    interface MonthlyTotal {
        java.sql.Timestamp getM();

        BigDecimal getTotal();
    }
}
