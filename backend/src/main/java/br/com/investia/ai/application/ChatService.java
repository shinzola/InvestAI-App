package br.com.investia.ai.application;

import br.com.investia.ai.domain.ChatMessage;
import br.com.investia.ai.infrastructure.jpa.ChatMessageJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Copiloto conversacional do InvestIA.
 *
 * Com provedor de LLM configurado, opera como agente: recebe o histórico da
 * conversa e um retrato financeiro real, escolhe ferramentas, vê os resultados
 * e só então redige a resposta — podendo encadear várias ações na mesma frase.
 *
 * Sem provedor, cai num interpretador determinístico em pt-BR que também
 * mantém contexto entre turnos (responde a "e o mês passado?", "qual foi o valor?").
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final Set<String> TOOLS = Set.of(
            "create_income", "create_expense", "create_planned_expense",
            "mark_planned_paid", "create_goal", "contribute_goal",
            "summary", "list_accounts", "list_planning");

    private static final int MAX_STEPS = 6;
    private static final int HISTORY_WINDOW = 16;

    private static final Pattern AMOUNT = Pattern.compile(
            "(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+(?:[.,]\\d{1,2})?)");
    private static final Pattern DATE = Pattern.compile(
            "(\\d{2}/\\d{2}(?:/\\d{4})?)");
    private static final Pattern AMOUNT_ONLY = Pattern.compile(
            "^\\s*(?:r\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+(?:[.,]\\d{1,2})?)\\s*(?:reais)?\\s*[.!]?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private final ChatMessageJpaRepository messages;
    private final ChatToolService tools;
    private final LlmClient llm;
    private final AiSettingsService settings;
    private final ObjectMapper mapper = new ObjectMapper();

    public record Action(String tool, String summary, Map<String, Object> data) {
    }

    public record ChatReply(String answer, List<Action> actions, boolean usedLlm) {
    }

    public record ChatEntry(String id, String role, String content, List<Action> actions, String createdAt) {
    }

    /**
     * Sem transação envolvendo a chamada HTTP: cada gravação usa a transação
     * do repositório e as ferramentas abrem a sua, para não segurar conexão
     * do pool durante a latência do provedor.
     */
    public ChatReply send(UUID userId, String text) {
        String question = text == null ? "" : text.trim();
        if (question.isEmpty()) {
            return new ChatReply("Pode escrever — estou ouvindo.", List.of(), false);
        }
        save(userId, ChatMessage.Role.USER, question, "[]");

        List<ChatMessage> history = messages.findAllByUserIdOrderByCreatedAtAsc(userId);
        LlmConfig config = settings.resolve(userId);

        ChatReply reply = llm.enabled(config)
                ? converse(userId, config, history)
                : deterministic(userId, question, history);

        save(userId, ChatMessage.Role.ASSISTANT, reply.answer(), toJson(reply.actions()));
        return reply;
    }

    @Transactional(readOnly = true)
    public List<ChatEntry> history(UUID userId) {
        return messages.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(m -> new ChatEntry(m.getId().toString(), m.getRole().name(), m.getContent(),
                        parseActions(m.getActions()), m.getCreatedAt().toString()))
                .toList();
    }

    @Transactional
    public void clear(UUID userId) {
        messages.deleteAllByUserId(userId);
    }

    // ---------- agente com LLM ----------

    private ChatReply converse(UUID userId, LlmConfig config, List<ChatMessage> history) {
        List<LlmClient.Message> conversation = new ArrayList<>();
        conversation.add(LlmClient.Message.system(systemPrompt(userId)));
        for (ChatMessage m : tail(history, HISTORY_WINDOW)) {
            if (m.getRole() == ChatMessage.Role.USER) {
                conversation.add(LlmClient.Message.user(m.getContent()));
            } else {
                conversation.add(LlmClient.Message.assistant(m.getContent(), List.of()));
            }
        }

        List<Action> actions = new ArrayList<>();
        List<LlmClient.ToolSpec> specs = tools.toolSpecs();

        for (int step = 0; step < MAX_STEPS; step++) {
            LlmClient.Completion completion;
            try {
                completion = llm.chat(config, conversation, specs);
            } catch (RuntimeException e) {
                log.warn("LLM indisponível no passo {}: {}", step, e.getMessage());
                ChatReply fallback = deterministic(userId, lastUser(history), history);
                return new ChatReply(fallback.answer(), fallback.actions(), false);
            }
            if (completion == null) {
                ChatReply fallback = deterministic(userId, lastUser(history), history);
                return new ChatReply(fallback.answer(), fallback.actions(), false);
            }
            if (!completion.hasToolCalls()) {
                String answer = completion.content();
                if (answer == null || answer.isBlank()) {
                    answer = actions.isEmpty()
                            ? "Não entendi bem. Pode reformular?"
                            : actions.get(actions.size() - 1).summary();
                }
                return new ChatReply(answer, actions, true);
            }

            conversation.add(LlmClient.Message.assistant(completion.content(), completion.toolCalls()));
            for (LlmClient.ToolCall call : completion.toolCalls()) {
                conversation.add(LlmClient.Message.tool(call.id(), runTool(userId, call, actions)));
            }
        }

        String answer = actions.isEmpty()
                ? "Preciso de mais detalhes para concluir isso."
                : actions.get(actions.size() - 1).summary();
        return new ChatReply(answer, actions, true);
    }

    private String runTool(UUID userId, LlmClient.ToolCall call, List<Action> actions) {
        String name = call.name() == null ? "" : call.name().trim();
        if (!TOOLS.contains(name)) {
            return "{\"error\":\"ferramenta desconhecida: " + name + "\"}";
        }
        try {
            Map<String, Object> args = parseArgs(call.arguments());
            ChatToolService.ToolResult result = tools.execute(userId, name, args);
            actions.add(new Action(result.tool(), result.summary(), result.data()));
            return mapper.writeValueAsString(Map.of(
                    "ok", true,
                    "summary", result.summary(),
                    "data", result.data()));
        } catch (RuntimeException e) {
            log.warn("Ferramenta {} falhou: {}", name, e.getMessage());
            return "{\"ok\":false,\"error\":\"" + escape(e.getMessage()) + "\"}";
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"falha ao serializar resultado\"}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String systemPrompt(UUID userId) {
        return """
                Você é o InvestIA, copiloto financeiro pessoal de um usuário brasileiro.
                Converse em português do Brasil, com tom próximo e direto, como um consultor
                que conhece os dados dele. Não use jargão desnecessário e não repita o pedido.

                Como agir:
                - Se o usuário pedir uma ação (registrar, planejar, aportar, marcar como pago),
                  chame a ferramenta adequada. Pode chamar várias na mesma resposta.
                - Depois de executar, responda em linguagem natural confirmando o que mudou,
                  citando os valores reais devolvidos pelas ferramentas.
                - Se faltar um dado essencial (valor, nome da meta), pergunte objetivamente
                  em vez de chutar. Nunca invente valores, datas ou saldos.
                - Para perguntas sobre a situação financeira, use o retrato abaixo e/ou a
                  ferramenta summary. Pode comparar, opinar e sugerir próximos passos.
                - Se o usuário só conversar, converse: responda à altura, sem empurrar ação.
                - Seja conciso: no máximo 4 frases, salvo quando ele pedir detalhe.

                Retrato financeiro real do usuário:
                """ + tools.contextSnapshot(userId);
    }

    // ---------- interpretador determinístico (sem LLM) ----------

    private ChatReply deterministic(UUID userId, String question, List<ChatMessage> history) {
        Map<String, Object> intent = resolveDeterministic(question, history);
        if (intent == null) {
            if (asksActionWithoutAmount(question)) {
                return new ChatReply(
                        "Qual foi o valor? Me diga o número e eu registro na hora.",
                        List.of(), false);
            }
            return new ChatReply(helpText(), List.of(), false);
        }
        String tool = String.valueOf(intent.get("tool"));
        @SuppressWarnings("unchecked")
        Map<String, Object> args = intent.get("args") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        try {
            ChatToolService.ToolResult result = tools.execute(userId, tool, args);
            return new ChatReply(result.summary(),
                    List.of(new Action(result.tool(), result.summary(), result.data())), false);
        } catch (RuntimeException e) {
            log.warn("Falha ao executar {}: {}", tool, e.getMessage());
            return new ChatReply("Não consegui executar: " + e.getMessage(), List.of(), false);
        }
    }

    private Map<String, Object> resolveDeterministic(String question, List<ChatMessage> history) {
        String q = question.toLowerCase(Locale.ROOT);
        if (q.isBlank()) {
            return null;
        }
        String amount = firstMatch(AMOUNT, question);
        String date = firstMatch(DATE, question);

        // Continuidade: o usuário respondeu só com o valor a uma pergunta anterior.
        if (amount != null && AMOUNT_ONLY.matcher(question).matches() && lastAssistantAskedValue(history)) {
            String previous = lastUserMessage(history);
            if (previous != null) {
                Map<String, Object> retry = resolveDeterministic(previous + " " + amount, List.of());
                if (retry != null) {
                    return retry;
                }
            }
        }

        String description = cleanDescription(question, amount, date);

        if (q.contains("marcar") && q.contains("pag") || q.contains("quitar") || q.contains("já paguei")) {
            return intent("mark_planned_paid", Map.of("description", description));
        }
        if (q.contains("aporte") || q.contains("aportar")) {
            if (amount != null) {
                Map<String, Object> args = new LinkedHashMap<>();
                args.put("amount", amount);
                args.put("goal", description);
                if (date != null) {
                    args.put("date", date);
                }
                return intent("contribute_goal", args);
            }
        }
        if (q.contains("meta") && anyStem(q, "cri", "nova", "novo", "abrir", "montar", "quero", "fazer", "definir")) {
            if (amount == null) {
                return null;
            }
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("name", description.isBlank() ? "Nova meta" : description);
            args.put("targetAmount", amount);
            if (date != null) {
                args.put("targetDate", date);
            }
            return intent("create_goal", args);
        }
        if (q.contains("planej") || q.contains("agendar") || q.contains("vai vencer")) {
            if (amount != null) {
                Map<String, Object> args = new LinkedHashMap<>();
                args.put("description", description);
                args.put("amount", amount);
                if (date != null) {
                    args.put("dueDate", date);
                }
                if (q.contains("cartão") || q.contains("cartao")) {
                    args.put("kind", "CREDIT_CARD");
                }
                return intent("create_planned_expense", args);
            }
        }
        if (q.contains("recebi") || q.contains("entrou") || q.contains("caiu")
                || q.contains("salário") || q.contains("salario") || q.contains("feriado")) {
            if (amount != null) {
                Map<String, Object> args = new LinkedHashMap<>();
                args.put("amount", amount);
                args.put("description", description);
                args.put("incomeKind", inferIncomeKind(q));
                if (date != null) {
                    args.put("date", date);
                }
                return intent("create_income", args);
            }
        }
        if (anyStem(q, "gastei", "gasto", "gastar", "paguei", "pagar", "comprei", "compra", "despesa")) {
            if (amount != null) {
                Map<String, Object> args = new LinkedHashMap<>();
                args.put("amount", amount);
                args.put("description", description);
                if (date != null) {
                    args.put("date", date);
                }
                return intent("create_expense", args);
            }
        }
        if (q.contains("conta") && (q.contains("quais") || q.contains("listar") || q.contains("minhas"))) {
            return intent("list_accounts", Map.of());
        }
        if (q.contains("planejamento") || q.contains("gastos do mês") || q.contains("gastos do mes")) {
            return intent("list_planning", Map.of());
        }
        if (q.contains("resumo") || q.contains("como estou") || q.contains("saldo")
                || q.contains("quanto") || q.contains("sobra")) {
            return intent("summary", Map.of());
        }
        return null;
    }

    private static boolean asksActionWithoutAmount(String question) {
        String q = question.toLowerCase(Locale.ROOT);
        boolean verb = anyStem(q, "gastei", "gasto", "gastar", "paguei", "pagar", "comprei", "compra",
                "recebi", "entrou", "caiu", "planej", "aporte", "aportar")
                || (q.contains("meta") && anyStem(q, "cri", "nova", "novo", "abrir", "montar", "quero", "fazer", "definir"));
        return verb && firstMatch(AMOUNT, question) == null;
    }

    private static boolean lastAssistantAskedValue(List<ChatMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage m = history.get(i);
            if (m.getRole() == ChatMessage.Role.ASSISTANT) {
                String c = m.getContent() == null ? "" : m.getContent().toLowerCase(Locale.ROOT);
                return c.contains("qual foi o valor") || c.contains("qual o valor");
            }
        }
        return false;
    }

    private static String lastUserMessage(List<ChatMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage m = history.get(i);
            if (m.getRole() == ChatMessage.Role.USER) {
                return m.getContent();
            }
        }
        return null;
    }

    private static String lastUser(List<ChatMessage> history) {
        String last = lastUserMessage(history);
        return last == null ? "" : last;
    }

    private static List<ChatMessage> tail(List<ChatMessage> list, int size) {
        if (list.size() <= size) {
            return list;
        }
        return list.subList(list.size() - size, list.size());
    }

    private static String helpText() {
        return "Posso registrar entradas e despesas, planejar gastos, marcar contas como pagas, "
                + "criar metas e aportar. Diga por exemplo: \"gastei 45,90 no mercado hoje\".";
    }

    /** Verdadeiro se a frase contém algum dos radicais (cobre cria/criar/crie/criando). */
    private static boolean anyStem(String q, String... stems) {
        for (String s : stems) {
            if (q.contains(s)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> intent(String tool, Map<String, Object> args) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tool", tool);
        map.put("args", args);
        return map;
    }

    private static String inferIncomeKind(String q) {
        if (q.contains("salário") || q.contains("salario")) {
            return "SALARY";
        }
        if (q.contains("feriado")) {
            return "PAID_HOLIDAY";
        }
        if (q.contains("dividendo") || q.contains("provento")) {
            return "DIVIDEND";
        }
        if (q.contains("aporte")) {
            return "CONTRIBUTION";
        }
        if (q.contains("extra") || q.contains("freelance")) {
            return "EXTRA";
        }
        if (q.contains("estimativa") || q.contains("previs")) {
            return "ESTIMATE";
        }
        return "OTHER";
    }

    private static String firstMatch(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String cleanDescription(String question, String amount, String date) {
        String s = question;
        if (amount != null) {
            s = s.replace(amount, " ");
        }
        if (date != null) {
            s = s.replace(date, " ");
        }
        s = s.replaceAll("(?i)\\b(gastei|gasto|gastar|paguei|pagar|comprei|compra|recebi|entrou|caiu|"
                + "registre|registrar|planeje|planejar|agende|agendar|cria|crie|criar|criando|"
                + "quero|queria|fazer|abrir|montar|definir|nova|novo|meta|metas|objetivo|"
                + "marcar|como|pago|quitar|"
                + "no|na|de|do|da|em|um|uma|reais|r\\$|hoje|ontem|amanhã|amanha)\\b", " ");
        s = s.replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").trim();
        return s.length() > 80 ? s.substring(0, 80).trim() : s;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "'").replace("\n", " ");
    }

    // ---------- persistência ----------

    private void save(UUID userId, ChatMessage.Role role, String content, String actionsJson) {
        messages.save(ChatMessage.builder()
                .userId(userId)
                .role(role)
                .content(content)
                .actions(actionsJson)
                .build());
    }

    private String toJson(List<Action> actions) {
        try {
            return mapper.writeValueAsString(actions);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<Action> parseActions(String json) {
        try {
            return mapper.readValue(json, mapper.getTypeFactory()
                    .constructCollectionType(List.class, Action.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    static BigDecimal parseAmount(String raw) {
        String s = raw.replace(".", "").replace(",", ".");
        return new BigDecimal(s);
    }

    static LocalDate today() {
        return LocalDate.now();
    }
}
