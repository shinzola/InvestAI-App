package br.com.investia.analytics.application;

import br.com.investia.accounts.application.AccountService;
import br.com.investia.budgeting.api.dto.BudgetDtos.BudgetResponse;
import br.com.investia.budgeting.application.BudgetService;
import br.com.investia.portfolio.application.PortfolioService;
import br.com.investia.portfolio.application.RebalancingService;
import br.com.investia.shared.domain.PeriodRange;
import br.com.investia.transactions.application.TransactionQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Sumário do dashboard, evolução patrimonial, alocação e rebalanceamento. */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TransactionQueryPort transactions;
    private final AccountService accounts;
    private final PortfolioService portfolio;
    private final BudgetService budgets;
    private final RiskService riskService;
    private final RebalancingService rebalancing;

    @Transactional(readOnly = true)
    public Summary summary(UUID userId, int year, int month) {
        var period = PeriodRange.ofMonth(year, month);
        var prev = period.start().minusMonths(1);
        var prevPeriod = PeriodRange.ofMonth(prev.getYear(), prev.getMonthValue());

        BigDecimal income = transactions.income(userId, period.start(), period.end());
        BigDecimal expense = transactions.expense(userId, period.start(), period.end());
        BigDecimal prevIncome = transactions.income(userId, prevPeriod.start(), prevPeriod.end());
        BigDecimal prevExpense = transactions.expense(userId, prevPeriod.start(), prevPeriod.end());

        BigDecimal cash = accounts.list(userId).stream()
                .map(a -> a.balance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<br.com.investia.portfolio.api.dto.PortfolioDtos.PositionResponse> positions =
                portfolio.listPositions(userId);
        BigDecimal portfolioValue = positions.stream()
                .map(p -> p.marketValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netWorth = cash.add(portfolioValue);

        BudgetResponse budget = budgets.getBudget(userId, year, month);

        return new Summary(
                income, expense, income.subtract(expense),
                pctChange(prevExpense, expense), pctChange(prevIncome, income),
                cash, portfolioValue, netWorth,
                budget.totalSpentPct());
    }

    @Transactional(readOnly = true)
    public RiskService.RiskMetrics risk(UUID userId, int months) {
        var period = PeriodRange.lastMonths(months, LocalDate.now());
        return riskService.metrics(userId, period.start(), period.end());
    }

    @Transactional(readOnly = true)
    public RebalancingService.AllocationReport allocation(UUID userId) {
        return rebalancing.allocation(userId);
    }

    @Transactional(readOnly = true)
    public RebalancingService.AllocationReport rebalancing(UUID userId) {
        return rebalancing.allocation(userId);
    }

    private BigDecimal pctChange(BigDecimal base, BigDecimal current) {
        if (base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return current.subtract(base).multiply(BigDecimal.valueOf(100))
                .divide(base, 2, RoundingMode.HALF_UP);
    }

    public record Summary(
            BigDecimal income, BigDecimal expense, BigDecimal balance,
            BigDecimal expenseChangePct, BigDecimal incomeChangePct,
            BigDecimal cashBalance, BigDecimal portfolioValue, BigDecimal netWorth,
            double budgetUsedPct) {
    }
}
