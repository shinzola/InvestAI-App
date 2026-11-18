package br.com.investia.transactions.application;

import br.com.investia.transactions.infrastructure.jpa.TransactionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Adaptador da porta de leitura sobre o repositório JPA de transações. */
@Service
@RequiredArgsConstructor
public class TransactionQueryAdapter implements TransactionQueryPort {

    private final TransactionJpaRepository transactions;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> expenseTotalsByCategory(UUID userId, LocalDate start, LocalDate end) {
        return transactions.sumExpensesByCategory(userId, start, end).stream()
                .filter(t -> t.getCategoryId() != null)
                .collect(Collectors.toMap(TransactionJpaRepository.CategoryTotal::getCategoryId,
                        t -> t.getTotal() == null ? BigDecimal.ZERO : t.getTotal()));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal income(UUID userId, LocalDate start, LocalDate end) {
        return transactions.sumIncome(userId, start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal expense(UUID userId, LocalDate start, LocalDate end) {
        return transactions.sumExpense(userId, start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal avgMonthlyExpense(UUID userId, int months) {
        return transactions.avgMonthlyExpense(userId, LocalDate.now().minusMonths(months).withDayOfMonth(1));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal avgMonthlyEssentialExpense(UUID userId, int months) {
        return transactions.avgMonthlyEssentialExpense(userId,
                LocalDate.now().minusMonths(months).withDayOfMonth(1));
    }
}
