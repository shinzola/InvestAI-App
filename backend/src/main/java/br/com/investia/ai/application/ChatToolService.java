package br.com.investia.ai.application;

import br.com.investia.accounts.application.AccountService;
import br.com.investia.accounts.api.dto.AccountDtos.AccountResponse;
import br.com.investia.goals.api.dto.GoalDtos.ContributionRequest;
import br.com.investia.goals.api.dto.GoalDtos.GoalRequest;
import br.com.investia.goals.api.dto.GoalDtos.GoalResponse;
import br.com.investia.goals.application.GoalService;
import br.com.investia.goals.domain.GoalKind;
import br.com.investia.planning.api.dto.PlanningDtos.PlannedExpenseRequest;
import br.com.investia.planning.api.dto.PlanningDtos.PlannedExpenseResponse;
import br.com.investia.planning.application.PlanningService;
import br.com.investia.planning.domain.PlannedExpenseKind;
import br.com.investia.shared.domain.Money;
import br.com.investia.shared.domain.PeriodRange;
import br.com.investia.transactions.api.dto.TransactionDtos.TransactionRequest;
import br.com.investia.transactions.api.dto.TransactionDtos.TransactionResponse;
import br.com.investia.transactions.application.TransactionQueryPort;
import br.com.investia.transactions.application.TransactionService;
import br.com.investia.transactions.domain.IncomeKind;
import br.com.investia.transactions.domain.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Executa as ações pedidas no chat usando os serviços reais do domínio.
 * Nenhum valor é inventado: tudo passa por TransactionService, PlanningService
 * e GoalService, com as mesmas validações da API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatToolService {

    private final TransactionService transactions;
    private final PlanningService planning;
    private final GoalService goals;
    private final AccountService accounts;
    private final TransactionQueryPort query;

    public record ToolResult(String tool, String summary, Map<String, Object> data) {
    }

    /** Catálogo de ferramentas exposto ao LLM. */
    public String toolCatalog() {
        return """
                create_income(amount, description, date?, incomeKind?, account?)
                create_expense(amount, description, date?, account?)
                create_planned_expense(description, amount, dueDate?, kind?, card?)
                mark_planned_paid(description)
                create_goal(name, targetAmount, targetDate?)
                contribute_goal(goal, amount, date?)
                summary()
                list_accounts()
                list_planning(year?, month?)
                """;
    }

    /** Especificações com JSON Schema, usadas no tool calling do LLM. */
    public List<LlmClient.ToolSpec> toolSpecs() {
        return List.of(
                spec("create_expense", "Registra uma despesa já ocorrida.",
                        """
                        {"type":"object","properties":{
                          "amount":{"type":"number","description":"Valor em reais, ex.: 45.90"},
                          "description":{"type":"string","description":"Descrição curta do gasto"},
                          "date":{"type":"string","description":"Data ISO yyyy-MM-dd ou 'hoje'"},
                          "account":{"type":"string","description":"Nome da conta, opcional"}
                        },"required":["amount","description"]}"""),
                spec("create_income", "Registra uma entrada de dinheiro já recebida.",
                        """
                        {"type":"object","properties":{
                          "amount":{"type":"number"},
                          "description":{"type":"string"},
                          "date":{"type":"string"},
                          "incomeKind":{"type":"string","enum":["SALARY","CONTRIBUTION","ESTIMATE","PAID_HOLIDAY","EXTRA","DIVIDEND","OTHER"]},
                          "account":{"type":"string"}
                        },"required":["amount","description"]}"""),
                spec("create_planned_expense", "Planeja um gasto futuro do mês.",
                        """
                        {"type":"object","properties":{
                          "description":{"type":"string"},
                          "amount":{"type":"number"},
                          "dueDate":{"type":"string","description":"Data ISO yyyy-MM-dd"},
                          "kind":{"type":"string","enum":["FIXED","VARIABLE","CREDIT_CARD"]},
                          "card":{"type":"string","description":"Nome do cartão, quando kind=CREDIT_CARD"}
                        },"required":["description","amount"]}"""),
                spec("mark_planned_paid", "Marca um gasto planejado do mês como pago.",
                        """
                        {"type":"object","properties":{
                          "description":{"type":"string","description":"Trecho do nome do gasto"}
                        },"required":["description"]}"""),
                spec("create_goal", "Cria uma meta financeira.",
                        """
                        {"type":"object","properties":{
                          "name":{"type":"string"},
                          "targetAmount":{"type":"number"},
                          "targetDate":{"type":"string","description":"Data ISO yyyy-MM-dd"}
                        },"required":["name","targetAmount"]}"""),
                spec("contribute_goal", "Aporta um valor em uma meta existente.",
                        """
                        {"type":"object","properties":{
                          "goal":{"type":"string","description":"Trecho do nome da meta"},
                          "amount":{"type":"number"},
                          "date":{"type":"string"}
                        },"required":["goal","amount"]}"""),
                spec("summary", "Resumo financeiro do mês corrente.", """
                        {"type":"object","properties":{}}"""),
                spec("list_accounts", "Lista as contas do usuário.", """
                        {"type":"object","properties":{}}"""),
                spec("list_planning", "Lista os gastos planejados de um mês.", """
                        {"type":"object","properties":{
                          "year":{"type":"integer"},"month":{"type":"integer"}
                        }}"""));
    }

    private static LlmClient.ToolSpec spec(String name, String description, String schema) {
        return new LlmClient.ToolSpec(name, description, schema);
    }

    /**
     * Retrato financeiro real do usuário, injetado no prompt de sistema.
     * É o que permite ao modelo conversar com números verdadeiros.
     */
    @Transactional(readOnly = true)
    public String contextSnapshot(UUID userId) {
        LocalDate now = LocalDate.now();
        StringBuilder sb = new StringBuilder();
        sb.append("Data de hoje: ").append(now).append('\n');

        List<AccountResponse> accs = accounts.list(userId);
        sb.append("Contas (").append(accs.size()).append("):\n");
        for (AccountResponse a : accs) {
            sb.append("  - ").append(a.name())
                    .append(" | tipo ").append(a.type())
                    .append(" | saldo ").append(brl(a.balance() == null ? BigDecimal.ZERO : a.balance()));
            if (a.card() != null) {
                sb.append(" | cartão limite ").append(brl(a.card().limitTotal()))
                        .append(" usado ").append(brl(a.card().limitUsed()))
                        .append(" fecha dia ").append(a.card().closingDay())
                        .append(" vence dia ").append(a.card().dueDay());
            }
            sb.append('\n');
        }

        PeriodRange month = PeriodRange.ofMonth(now.getYear(), now.getMonthValue());
        BigDecimal income = query.income(userId, month.start(), month.end());
        BigDecimal expense = query.expense(userId, month.start(), month.end());
        var plan = planning.summary(userId, now.getYear(), now.getMonthValue());
        sb.append("Mês corrente (").append(now.getYear()).append('-')
                .append(String.format("%02d", now.getMonthValue())).append("):\n")
                .append("  entradas ").append(brl(income))
                .append(" | despesas ").append(brl(expense))
                .append(" | saldo ").append(brl(Money.of(income).subtract(Money.of(expense)).value()))
                .append('\n')
                .append("  planejado ").append(brl(plan.total()))
                .append(" | pago ").append(brl(plan.paid()))
                .append(" | sobra para aportar ").append(brl(plan.availableToContribute()))
                .append('\n');

        List<GoalResponse> gs = goals.list(userId);
        sb.append("Metas (").append(gs.size()).append("):\n");
        for (GoalResponse g : gs) {
            sb.append("  - ").append(g.name()).append(" | guardado ").append(brl(g.saved()))
                    .append(" de ").append(brl(g.targetAmount()))
                    .append(" (").append(String.format("%.0f", g.progressPct())).append("%)\n");
        }

        List<PlannedExpenseResponse> planned = planning.list(userId, now.getYear(), now.getMonthValue());
        sb.append("Gastos planejados do mês (").append(planned.size()).append("):\n");
        for (PlannedExpenseResponse p : planned) {
            sb.append("  - ").append(p.description()).append(" | ").append(brl(p.amount()))
                    .append(" | vence ").append(p.dueDate())
                    .append(" | ").append(p.kind())
                    .append(" | ").append(p.status()).append('\n');
        }
        return sb.toString();
    }

    @Transactional
    public ToolResult execute(UUID userId, String tool, Map<String, Object> args) {
        Map<String, Object> a = args == null ? Map.of() : args;
        return switch (tool) {
            case "create_income" -> createTransaction(userId, a, TransactionType.INCOME);
            case "create_expense" -> createTransaction(userId, a, TransactionType.EXPENSE);
            case "create_planned_expense" -> createPlanned(userId, a);
            case "mark_planned_paid" -> markPaid(userId, a);
            case "create_goal" -> createGoal(userId, a);
            case "contribute_goal" -> contributeGoal(userId, a);
            case "summary" -> summary(userId);
            case "list_accounts" -> listAccounts(userId);
            case "list_planning" -> listPlanning(userId, a);
            default -> new ToolResult(tool, "Não sei executar \"" + tool + "\".", Map.of());
        };
    }

    private ToolResult createTransaction(UUID userId, Map<String, Object> a, TransactionType type) {
        BigDecimal amount = toAmount(a.get("amount"));
        String description = text(a.get("description"), type == TransactionType.INCOME ? "Entrada" : "Despesa");
        LocalDate date = toDate(a.get("date"));
        AccountResponse account = resolveAccount(userId, a.get("account"));
        IncomeKind kind = type == TransactionType.INCOME
                ? toIncomeKind(a.get("incomeKind"))
                : null;

        TransactionResponse created = transactions.create(userId, new TransactionRequest(
                type, UUID.fromString(account.id()), null, null, null,
                amount, date, description, "Registrado pelo chat", null, null, kind, null));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", created.id());
        data.put("type", created.type());
        data.put("amount", created.amount());
        data.put("description", created.description());
        data.put("date", created.date().toString());
        data.put("account", account.name());

        String label = type == TransactionType.INCOME ? "Entrada" : "Despesa";
        return new ToolResult(type == TransactionType.INCOME ? "create_income" : "create_expense",
                "%s de %s registrada em %s (%s).".formatted(
                        label, brl(amount), date, account.name()), data);
    }

    private ToolResult createPlanned(UUID userId, Map<String, Object> a) {
        BigDecimal amount = toAmount(a.get("amount"));
        String description = text(a.get("description"), "Gasto planejado");
        LocalDate due = toDate(a.get("dueDate"));
        PlannedExpenseKind kind = toPlannedKind(a.get("kind"), a.get("card"));
        UUID cardId = null;
        if (kind == PlannedExpenseKind.CREDIT_CARD) {
            cardId = UUID.fromString(resolveAccount(userId, a.get("card")).id());
        }

        PlannedExpenseResponse created = planning.create(userId, new PlannedExpenseRequest(
                description, amount, null, due, kind, null, "Criado pelo chat", cardId));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", created.id());
        data.put("description", created.description());
        data.put("amount", created.amount());
        data.put("dueDate", created.dueDate().toString());
        data.put("kind", created.kind());
        if (created.invoiceDueDate() != null) {
            data.put("invoiceDueDate", created.invoiceDueDate().toString());
        }
        return new ToolResult("create_planned_expense",
                "Planejei \"%s\" de %s para %s.".formatted(description, brl(amount), due), data);
    }

    private ToolResult markPaid(UUID userId, Map<String, Object> a) {
        String wanted = text(a.get("description"), "").toLowerCase(Locale.ROOT);
        LocalDate now = LocalDate.now();
        List<PlannedExpenseResponse> month = planning.list(userId, now.getYear(), now.getMonthValue());
        PlannedExpenseResponse target = month.stream()
                .filter(p -> p.description().toLowerCase(Locale.ROOT).contains(wanted))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return new ToolResult("mark_planned_paid",
                    "Não encontrei um gasto com \"" + wanted + "\" neste mês.", Map.of());
        }
        PlannedExpenseResponse updated = planning.togglePaid(userId, UUID.fromString(target.id()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", updated.id());
        data.put("description", updated.description());
        data.put("status", updated.status());
        return new ToolResult("mark_planned_paid",
                "\"%s\" agora está %s.".formatted(updated.description(),
                        "PAID".equals(updated.status()) ? "pago" : "previsto"), data);
    }

    private ToolResult createGoal(UUID userId, Map<String, Object> a) {
        BigDecimal target = toAmount(a.get("targetAmount"));
        String name = text(a.get("name"), "Nova meta");
        LocalDate targetDate = a.get("targetDate") == null ? null : toDate(a.get("targetDate"));
        GoalResponse created = goals.create(userId, new GoalRequest(
                name, GoalKind.OUTRO, target, targetDate, null));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", created.id());
        data.put("name", created.name());
        data.put("targetAmount", created.targetAmount());
        return new ToolResult("create_goal",
                "Meta \"%s\" criada com alvo de %s.".formatted(name, brl(target)), data);
    }

    private ToolResult contributeGoal(UUID userId, Map<String, Object> a) {
        BigDecimal amount = toAmount(a.get("amount"));
        String wanted = text(a.get("goal"), "").toLowerCase(Locale.ROOT);
        GoalResponse target = goals.list(userId).stream()
                .filter(g -> g.name().toLowerCase(Locale.ROOT).contains(wanted))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return new ToolResult("contribute_goal",
                    "Não encontrei uma meta com \"" + wanted + "\".", Map.of());
        }
        GoalResponse updated = goals.contribute(userId, UUID.fromString(target.id()),
                new ContributionRequest(amount, toDate(a.get("date")), "Aporte pelo chat"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", updated.id());
        data.put("name", updated.name());
        data.put("saved", updated.saved());
        data.put("progressPct", updated.progressPct());
        return new ToolResult("contribute_goal",
                "Aportei %s em \"%s\". Progresso: %.0f%%.".formatted(
                        brl(amount), updated.name(), updated.progressPct()), data);
    }

    private ToolResult summary(UUID userId) {
        LocalDate now = LocalDate.now();
        PeriodRange month = PeriodRange.ofMonth(now.getYear(), now.getMonthValue());
        BigDecimal income = query.income(userId, month.start(), month.end());
        BigDecimal expense = query.expense(userId, month.start(), month.end());
        BigDecimal net = Money.of(income).subtract(Money.of(expense)).value();
        var plan = planning.summary(userId, now.getYear(), now.getMonthValue());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("income", income);
        data.put("expense", expense);
        data.put("balance", net);
        data.put("plannedTotal", plan.total());
        data.put("availableToContribute", plan.availableToContribute());
        return new ToolResult("summary",
                "Neste mês: entradas %s, despesas %s, saldo %s. Planejado %s, sobra para aportar %s."
                        .formatted(brl(income), brl(expense), brl(net),
                                brl(plan.total()), brl(plan.availableToContribute())), data);
    }

    private ToolResult listAccounts(UUID userId) {
        List<AccountResponse> list = accounts.list(userId);
        List<Map<String, Object>> data = new ArrayList<>();
        for (AccountResponse acc : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", acc.id());
            item.put("name", acc.name());
            item.put("type", acc.type());
            item.put("institution", acc.institution());
            item.put("hasCreditCard", acc.card() != null);
            data.add(item);
        }
        String names = list.isEmpty() ? "nenhuma conta"
                : String.join(", ", list.stream().map(AccountResponse::name).toList());
        return new ToolResult("list_accounts", "Suas contas: " + names + ".", Map.of("accounts", data));
    }

    private ToolResult listPlanning(UUID userId, Map<String, Object> a) {
        LocalDate now = LocalDate.now();
        int year = a.get("year") == null ? now.getYear() : toInt(a.get("year"), now.getYear());
        int month = a.get("month") == null ? now.getMonthValue() : toInt(a.get("month"), now.getMonthValue());
        List<PlannedExpenseResponse> list = planning.list(userId, year, month);
        List<Map<String, Object>> data = new ArrayList<>();
        for (PlannedExpenseResponse p : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.id());
            item.put("description", p.description());
            item.put("amount", p.amount());
            item.put("dueDate", p.dueDate().toString());
            item.put("kind", p.kind());
            item.put("status", p.status());
            data.add(item);
        }
        return new ToolResult("list_planning",
                "Você tem %d gastos planejados em %02d/%d.".formatted(list.size(), month, year),
                Map.of("items", data));
    }

    // ---------- resolução de argumentos ----------

    private AccountResponse resolveAccount(UUID userId, Object ref) {
        List<AccountResponse> list = accounts.list(userId);
        if (list.isEmpty()) {
            throw new IllegalStateException("Nenhuma conta cadastrada. Crie uma conta em Ajustes.");
        }
        String wanted = ref == null ? "" : String.valueOf(ref).toLowerCase(Locale.ROOT).trim();
        if (!wanted.isBlank()) {
            for (AccountResponse acc : list) {
                String haystack = (acc.name() + " " + (acc.institution() == null ? "" : acc.institution()))
                        .toLowerCase(Locale.ROOT);
                if (haystack.contains(wanted) || acc.id().equalsIgnoreCase(wanted)) {
                    return acc;
                }
            }
        }
        return list.get(0);
    }

    private static String text(Object v, String fallback) {
        if (v == null) {
            return fallback;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? fallback : s;
    }

    private static BigDecimal toAmount(Object v) {
        if (v == null) {
            throw new IllegalArgumentException("Valor não informado.");
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).setScale(2, java.math.RoundingMode.HALF_UP);
        }
        String s = String.valueOf(v).replaceAll("[^0-9,.-]", "").trim();
        if (s.contains(",")) {
            s = s.replace(".", "").replace(",", ".");
        }
        BigDecimal value = new BigDecimal(s);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("O valor precisa ser maior que zero.");
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static LocalDate toDate(Object v) {
        if (v == null) {
            return LocalDate.now();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "hoje".equalsIgnoreCase(s)) {
            return LocalDate.now();
        }
        if (s.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.parse(s);
        }
        if (s.matches("\\d{2}/\\d{2}/\\d{4}")) {
            String[] p = s.split("/");
            return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
        }
        if (s.matches("\\d{2}/\\d{2}")) {
            String[] p = s.split("/");
            return YearMonth.now().atDay(Integer.parseInt(p[0]))
                    .withMonth(Integer.parseInt(p[1]));
        }
        return LocalDate.now();
    }

    private static int toInt(Object v, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static IncomeKind toIncomeKind(Object v) {
        if (v == null) {
            return IncomeKind.OTHER;
        }
        try {
            return IncomeKind.valueOf(String.valueOf(v).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return IncomeKind.OTHER;
        }
    }

    private static PlannedExpenseKind toPlannedKind(Object v, Object card) {
        if (v != null) {
            try {
                return PlannedExpenseKind.valueOf(String.valueOf(v).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // cai na inferência abaixo
            }
        }
        return card == null ? PlannedExpenseKind.VARIABLE : PlannedExpenseKind.CREDIT_CARD;
    }

    private static String brl(BigDecimal v) {
        return "R$ " + v.setScale(2, java.math.RoundingMode.HALF_UP)
                .toPlainString().replace(".", ",");
    }
}
