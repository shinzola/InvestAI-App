package br.com.investia.ai.application;

import br.com.investia.ai.domain.AiInsight;
import br.com.investia.ai.infrastructure.jpa.AiInsightJpaRepository;
import br.com.investia.budgeting.application.events.BudgetAlertRaisedEvent;
import br.com.investia.shared.domain.PeriodRange;
import br.com.investia.transactions.application.TransactionQueryPort;
import br.com.investia.transactions.application.events.TransactionCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.math.RoundingMode;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Geração de insights: narrativa mensal, alertas de orçamento e
 * anomalias de gasto (dispêndio > 1,42× a média = +42%).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightService {

    private final TransactionQueryPort transactions;
    private final AiInsightJpaRepository insights;

    /** Narrativa do mês com números reais (receita, despesa, saldo). */
    public void monthlyNarrative(UUID userId, int year, int month) {
        var period = PeriodRange.ofMonth(year, month);
        BigDecimal income = transactions.income(userId, period.start(), period.end());
        BigDecimal expense = transactions.expense(userId, period.start(), period.end());
        BigDecimal net = income.subtract(expense);

        String narrative = ("No mês %02d/%d você recebeu R$ %s e gastou R$ %s, "
                + "resultando em saldo de R$ %s (%s).")
                .formatted(month, year, income.toPlainString(), expense.toPlainString(),
                        net.toPlainString(),
                        net.signum() >= 0 ? "superávit" : "déficit — atenção ao orçamento");

        insights.save(AiInsight.builder()
                .userId(userId)
                .kind(AiInsight.InsightKind.NARRATIVE)
                .answer(narrative)
                .build());
    }

    /** Alerta de gasto atípico: dispêndio 42% acima da média dos últimos 3 meses. */
    @EventListener
    public void onTransaction(TransactionCreatedEvent event) {
        if (event.type() != br.com.investia.transactions.domain.TransactionType.EXPENSE) {
            return;
        }
        BigDecimal avg = transactions.avgMonthlyExpense(event.userId(), 3);
        if (avg.signum() == 0) {
            return;
        }
        // compara o total do mês corrente com a média
        var now = LocalDate.now();
        var month = PeriodRange.ofMonth(now.getYear(), now.getMonthValue());
        BigDecimal monthTotal = transactions.expense(event.userId(), month.start(), month.end());
        double delta = monthTotal.subtract(avg)
                .multiply(BigDecimal.valueOf(100))
                .divide(avg, 2, RoundingMode.HALF_UP).doubleValue();

        // 1450 vs 1000 => 45% acima da média
        if (delta >= 42) {
            String message = "Seus gastos deste mês estão %.0f%% acima da média dos últimos 3 meses (R$ %s vs R$ %s)."
                    .formatted(delta, monthTotal.toPlainString(), avg.toPlainString());
            insights.save(AiInsight.builder()
                    .userId(event.userId())
                    .kind(AiInsight.InsightKind.ALERT)
                    .answer(message)
                    .payload("{\"deltaPct\":" + delta + ",\"mesAtual\":" + monthTotal
                            + ",\"media3m\":" + avg + "}")
                    .build());
        }
    }

    /** Alerta de orçamento publicado pelo módulo budgeting. */
    @EventListener
    public void onBudgetAlert(BudgetAlertRaisedEvent event) {
        String message = "AT100".equals(event.level())
                ? "Orçamento de %s estourado: R$ %s de R$ %s (100%%)."
                        .formatted(event.categoryName(), event.spent().value().toPlainString(),
                                event.limit().value().toPlainString())
                : "Atenção: %s atingiu %s%% do orçamento (R$ %s de R$ %s)."
                        .formatted(event.categoryName(), "80",
                                event.spent().value().toPlainString(),
                                event.limit().value().toPlainString());
        insights.save(AiInsight.builder()
                .userId(event.userId())
                .kind(AiInsight.InsightKind.ALERT)
                .answer(message)
                .build());
    }

    public List<AiInsight> recent(UUID userId) {
        return insights.findRecent(userId);
    }
}
