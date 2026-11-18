package br.com.investia.transactions.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Porta de leitura do módulo transactions para outros módulos
 * (budgeting, analytics, ai). Mantém a dependência na direção correta.
 */
public interface TransactionQueryPort {

    /** Total de despesas por categoria no período (apenas CLEARED). */
    Map<UUID, BigDecimal> expenseTotalsByCategory(UUID userId, LocalDate start, LocalDate end);

    BigDecimal income(UUID userId, LocalDate start, LocalDate end);

    BigDecimal expense(UUID userId, LocalDate start, LocalDate end);

    /** Média mensal de despesas nos últimos n meses. */
    BigDecimal avgMonthlyExpense(UUID userId, int months);

    /** Média mensal de despesas essenciais (reserva de emergência). */
    BigDecimal avgMonthlyEssentialExpense(UUID userId, int months);
}
