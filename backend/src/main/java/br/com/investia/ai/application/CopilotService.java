package br.com.investia.ai.application;

import br.com.investia.ai.domain.AiInsight;
import br.com.investia.ai.infrastructure.jpa.AiInsightJpaRepository;
import br.com.investia.budgeting.application.BudgetService;
import br.com.investia.earnings.application.EarningsService;
import br.com.investia.identity.infrastructure.jpa.UserProfileJpaRepository;
import br.com.investia.portfolio.application.PortfolioService;
import br.com.investia.shared.domain.PeriodRange;
import br.com.investia.transactions.application.TransactionQueryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.investia.shared.domain.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Copiloto: responde perguntas SEMPRE derivado de consultas reais
 * (números do banco), com redação opcional via LLM. Nunca inventa dados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CopilotService {

    private final TransactionQueryPort transactions;
    private final PortfolioService portfolio;
    private final EarningsService earnings;
    private final BudgetService budgets;
    private final UserProfileJpaRepository profiles;
    private final AiInsightJpaRepository insights;
    private final LlmClient llm;
    private final AiSettingsService settings;

    @Transactional
    public CopilotAnswer ask(UUID userId, String question) {
        String q = question.toLowerCase();
        var now = LocalDate.now();
        var month = PeriodRange.ofMonth(now.getYear(), now.getMonthValue());

        BigDecimal income = transactions.income(userId, month.start(), month.end());
        BigDecimal expense = transactions.expense(userId, month.start(), month.end());
        BigDecimal net = Money.of(income).subtract(Money.of(expense)).value();

        Map<String, Object> payload = new LinkedHashMap<>();
        String answer;

        if (q.contains("gasto") || q.contains("despesa") || q.contains("gastei")) {
            payload.put("despesasMes", expense);
            answer = "Suas despesas deste mês somam R$ %s."
                    .formatted(expense.toPlainString());
        } else if (q.contains("renda passiva") || q.contains("dividendos") || q.contains("viver de renda")) {
            var summary = earnings.summary(userId);
            payload.put("mediaMensal12m", summary.averageLast12m());
            payload.put("rendaDesejada", summary.desiredMonthlyIncome());
            payload.put("faltante", summary.missingForDesiredIncome());
            if (q.contains("falta") || q.contains("viver")) {
                answer = "Sua média de renda passiva é R$ %s/mês. Para a meta de R$ %s/mês faltam R$ %s."
                        .formatted(summary.averageLast12m().toPlainString(),
                                summary.desiredMonthlyIncome().toPlainString(),
                                summary.missingForDesiredIncome().toPlainString());
            } else {
                answer = "Nos últimos 12 meses você recebeu R$ %s de proventos (média R$ %s/mês)."
                        .formatted(summary.totalLast12m().toPlainString(),
                                summary.averageLast12m().toPlainString());
            }
        } else if (q.contains("posição") || q.contains("carteira") || q.contains("acao") || q.contains("ação")) {
            var positions = portfolio.listPositions(userId);
            BigDecimal total = positions.stream()
                    .map(p -> BigDecimal.valueOf(p.marketValue().doubleValue()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            payload.put("valorCarteira", total);
            payload.put("posicoes", positions.size());
            answer = "Sua carteira tem %d posições somando R$ %s em valor de mercado."
                    .formatted(positions.size(), total.toPlainString());
        } else if (q.contains("orcamento") || q.contains("orçamento")) {
            var budget = budgets.getBudget(userId, now.getYear(), now.getMonthValue());
            payload.put("totalLimite", budget.totalLimit());
            payload.put("totalGasto", budget.totalSpent());
            payload.put("pctGasto", budget.totalSpentPct());
            answer = "Você usou %.2f%% do orçamento do mês (R$ %s de R$ %s)."
                    .formatted(budget.totalSpentPct(), budget.totalSpent().toPlainString(),
                            budget.totalLimit().toPlainString());
        } else if (q.contains("saldo") || q.contains("receita") || q.contains("salario") || q.contains("salário")) {
            payload.put("receitasMes", income);
            payload.put("despesasMes", expense);
            payload.put("saldoMes", net);
            answer = "Neste mês: receitas de R$ %s e despesas de R$ %s. Saldo de R$ %s."
                    .formatted(income.toPlainString(), expense.toPlainString(), net.toPlainString());
        } else {
            payload.put("receitasMes", income);
            payload.put("despesasMes", expense);
            payload.put("saldoMes", net);
            answer = "Resumo rápido: receitas R$ %s, despesas R$ %s, saldo R$ %s. "
                    .formatted(income.toPlainString(), expense.toPlainString(), net.toPlainString())
                    + "Pergunte sobre gastos, orçamento, carteira, proventos ou viver de renda.";
        }

        // Redação final via LLM (opcional) — mesmo contexto, sem novos números
        LlmConfig config = settings.resolve(userId);
        if (llm.enabled(config)) {
            LlmClient.Completion completion = llm.chat(config, List.of(
                    LlmClient.Message.system(
                            "Você é o copiloto financeiro InvestIA. Reescreva a resposta em pt-BR, "
                                    + "tom claro e amigável, usando SOMENTE os números fornecidos. "
                                    + "Nunca invente valores."),
                    LlmClient.Message.user("Pergunta: %s%nNúmeros reais: %s%nResposta base: %s"
                            .formatted(question, payload, answer))), List.of());
            String polished = completion == null ? null : completion.content();
            if (polished != null && !polished.isBlank()) {
                answer = polished.trim();
            }
        }

        insights.save(AiInsight.builder()
                .userId(userId)
                .kind(AiInsight.InsightKind.ANSWER)
                .question(question)
                .answer(answer)
                .payload(toJson(payload))
                .build());

        return new CopilotAnswer(answer, payload);
    }

    public record CopilotAnswer(String answer, Map<String, Object> dataUsed) {
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }
}
