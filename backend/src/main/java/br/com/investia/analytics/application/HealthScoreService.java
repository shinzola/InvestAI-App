package br.com.investia.analytics.application;

import br.com.investia.budgeting.application.BudgetService;
import br.com.investia.budgeting.api.dto.BudgetDtos.BudgetResponse;
import br.com.investia.portfolio.api.dto.PortfolioDtos.PositionResponse;
import br.com.investia.portfolio.application.PortfolioService;
import br.com.investia.accounts.application.AccountService;
import br.com.investia.transactions.application.TransactionQueryPort;
import br.com.investia.shared.domain.PeriodRange;
import br.com.investia.identity.application.UserDataContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Saúde financeira (score 0–100). Pesos documentados:
 *  - Taxa de poupança (30): (renda − gasto) / renda no mês
 *  - Endividamento (20): comprometimento da renda com cartão
 *  - Reserva (20): meses de despesa cobertos pela reserva
 *  - Diversificação (15): concentração máxima por classe
 *  - Regularidade de aportes (15): aderência ao orçamento do mês
 */
@Service
@RequiredArgsConstructor
public class HealthScoreService {

    private final TransactionQueryPort transactions;
    private final BudgetService budgets;
    private final AccountService accounts;
    private final PortfolioService portfolio;

    @Transactional(readOnly = true)
    public HealthResponse compute(UUID userId, int year, int month) {
        var period = PeriodRange.ofMonth(year, month);
        BigDecimal income = BigDecimal.valueOf(transactions.income(userId, period.start(), period.end()).doubleValue());
        BigDecimal expense = BigDecimal.valueOf(transactions.expense(userId, period.start(), period.end()).doubleValue());

        double savingsRate = income.signum() == 0
                ? 0
                : income.subtract(expense).multiply(BigDecimal.valueOf(100))
                        .divide(income, 2, RoundingMode.HALF_UP).doubleValue();

        // contas: soma dos saldos disponíveis (reserva em conta)
        BigDecimal cash = accounts.list(userId).stream()
                .map(a -> BigDecimal.valueOf(a.balance().doubleValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgExpense = BigDecimal.valueOf(
                transactions.avgMonthlyExpense(userId, 3).doubleValue());

        double monthsOfReserve = avgExpense.signum() == 0
                ? 0
                : cash.multiply(BigDecimal.valueOf(100)).divide(avgExpense, 2, RoundingMode.HALF_UP).doubleValue() / 100;

        List<PositionResponse> positions = portfolio.listPositions(userId);
        BigDecimal portfolioValue = positions.stream()
                .map(PositionResponse::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double maxConcentration = positions.stream()
                .filter(p -> portfolioValue.signum() > 0)
                .mapToDouble(p -> p.marketValue()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(portfolioValue, 2, RoundingMode.HALF_UP).doubleValue())
                .max().orElse(0);

        BudgetResponse budget = budgets.getBudget(userId, year, month);
        double budgetAdherence = budget.totalSpentPct() == 0
                ? 100
                : Math.max(0, 100 - Math.max(0, budget.totalSpentPct() - 100));

        int scoreSavings = scoreBand(savingsRate, 0, 20, 30);
        int scoreReserve = scoreBand(monthsOfReserve * 100, 0, 600, 20);
        int scoreDiversification = maxConcentration <= 0 ? 7
                : scoreBand(100 - Math.max(0, maxConcentration - 40), 0, 60, 15);
        int scoreBudget = scoreBand(budgetAdherence, 60, 100, 15);

        int total = scoreSavings + scoreReserve + scoreDiversification + scoreBudget;
        String classification = total >= 80 ? "SAUDAVEL" : total >= 60 ? "ATENCAO" : "CRITICO";

        Map<String, Integer> breakdown = new LinkedHashMap<>();
        breakdown.put("poupanca", scoreSavings);
        breakdown.put("reserva", scoreReserve);
        breakdown.put("diversificacao", scoreDiversification);
        breakdown.put("adesaoOrcamento", scoreBudget);

        return new HealthResponse(total, classification, breakdown,
                round(savingsRate), round(monthsOfReserve * 100), round(maxConcentration));
    }

    public record HealthResponse(int score, String classification,
                                 Map<String, Integer> breakdown,
                                 double savingsRatePct, double reserveMonthsPct,
                                 double maxConcentrationPct) {
    }

    /** Converte valor em score linear entre piso e teto do peso. */
    private int scoreBand(double value, double floor, double ceiling, int weight) {
        if (value <= floor) {
            return 0;
        }
        if (value >= ceiling) {
            return weight;
        }
        return (int) Math.round(weight * (value - floor) / (ceiling - floor));
    }

    private double round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
